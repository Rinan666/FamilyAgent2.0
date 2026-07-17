package com.familyagent.module.memory.eval;

import com.familyagent.module.memory.eval.dto.MemoryRecallQualityComparisonReport;
import com.familyagent.module.memory.eval.dto.MemoryRecallQualityEvalCase;
import com.familyagent.module.memory.eval.dto.MemoryRecallQualityEvalReport;
import com.familyagent.module.memory.eval.dto.MemoryRecallQualityEvalResult;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MemoryRecallQualityEvalService {

    private static final String REPORT_SCHEMA_VERSION = "memory.recall.quality.report.v1";
    private static final String COMPARISON_SCHEMA_VERSION = "memory.recall.quality.comparison.v1";

    public MemoryRecallQualityEvalReport evaluate(
            String suiteVersion,
            String algorithmVersion,
            List<MemoryRecallQualityEvalCase> cases) {
        List<MemoryRecallQualityEvalResult> results = safeCases(cases).stream()
                .map(this::evaluateCase)
                .toList();
        int expectedCount = results.stream().mapToInt(MemoryRecallQualityEvalResult::expectedSourceCount).sum();
        int hitCount = results.stream()
                .flatMap(result -> result.expectedRanks().stream())
                .mapToInt(rank -> rank > 0 ? 1 : 0)
                .sum();
        int firstHitCount = (int) results.stream().filter(MemoryRecallQualityEvalResult::firstResultHit).count();
        int unauthorizedCount = results.stream()
                .mapToInt(MemoryRecallQualityEvalResult::unauthorizedResultCount)
                .sum();
        return new MemoryRecallQualityEvalReport(
                REPORT_SCHEMA_VERSION,
                suiteVersion,
                algorithmVersion,
                new MemoryRecallQualityEvalReport.Metrics(
                        results.size(),
                        (int) results.stream().filter(MemoryRecallQualityEvalResult::passed).count(),
                        expectedCount,
                        hitCount,
                        rate(hitCount, expectedCount),
                        firstHitCount,
                        rate(firstHitCount, results.size()),
                        unauthorizedCount,
                        unauthorizedCount == 0),
                results);
    }

    public MemoryRecallQualityComparisonReport compare(
            MemoryRecallQualityEvalReport baseline,
            MemoryRecallQualityEvalReport candidate) {
        if (!comparable(baseline, candidate)) {
            return new MemoryRecallQualityComparisonReport(
                    COMPARISON_SCHEMA_VERSION,
                    version(baseline),
                    version(candidate),
                    MemoryRecallQualityComparisonReport.Conclusion.INCOMPARABLE,
                    new MemoryRecallQualityComparisonReport.Metrics(0, 0, 0, 0),
                    List.of());
        }

        Map<String, MemoryRecallQualityEvalResult> baselineResults = byCaseId(baseline.results());
        List<MemoryRecallQualityComparisonReport.CaseChange> changes = candidate.results().stream()
                .map(result -> caseChange(baselineResults.get(result.caseId()), result))
                .toList();
        int rankRegressions = (int) changes.stream()
                .filter(MemoryRecallQualityComparisonReport.CaseChange::regressed)
                .count();
        MemoryRecallQualityComparisonReport.Metrics metrics = new MemoryRecallQualityComparisonReport.Metrics(
                delta(candidate.metrics().expectedTopKHitRate(), baseline.metrics().expectedTopKHitRate()),
                delta(candidate.metrics().firstResultHitRate(), baseline.metrics().firstResultHitRate()),
                candidate.metrics().unauthorizedResultCount() - baseline.metrics().unauthorizedResultCount(),
                rankRegressions);
        return new MemoryRecallQualityComparisonReport(
                COMPARISON_SCHEMA_VERSION,
                baseline.algorithmVersion(),
                candidate.algorithmVersion(),
                conclusion(baseline, candidate, metrics),
                metrics,
                changes);
    }

    private MemoryRecallQualityEvalResult evaluateCase(MemoryRecallQualityEvalCase evalCase) {
        List<Integer> ranks = evalCase.expectedSourceIds().stream()
                .map(sourceId -> rank(evalCase.actualSourceIds(), sourceId))
                .toList();
        Set<String> unauthorized = new HashSet<>(evalCase.unauthorizedSourceIds());
        int unauthorizedCount = (int) evalCase.actualSourceIds().stream()
                .filter(unauthorized::contains)
                .distinct()
                .count();
        boolean topKHit = !ranks.isEmpty() && ranks.stream().allMatch(rank -> rank > 0);
        boolean firstHit = ranks.stream().anyMatch(rank -> rank == 1);
        return new MemoryRecallQualityEvalResult(
                evalCase.caseId(),
                evalCase.candidateCount(),
                ranks.size(),
                ranks,
                topKHit,
                firstHit,
                unauthorizedCount,
                topKHit && unauthorizedCount == 0);
    }

    private static boolean comparable(
            MemoryRecallQualityEvalReport baseline,
            MemoryRecallQualityEvalReport candidate) {
        if (baseline == null || candidate == null
                || !safe(baseline.suiteVersion()).equals(safe(candidate.suiteVersion()))) {
            return false;
        }
        Map<String, MemoryRecallQualityEvalResult> before = byCaseId(baseline.results());
        Map<String, MemoryRecallQualityEvalResult> after = byCaseId(candidate.results());
        if (!before.keySet().equals(after.keySet())) {
            return false;
        }
        return before.entrySet().stream().allMatch(entry ->
                entry.getValue().expectedSourceCount() == after.get(entry.getKey()).expectedSourceCount());
    }

    private static MemoryRecallQualityComparisonReport.CaseChange caseChange(
            MemoryRecallQualityEvalResult baseline,
            MemoryRecallQualityEvalResult candidate) {
        int before = baseline.bestExpectedRank();
        int after = candidate.bestExpectedRank();
        boolean regressed = candidate.unauthorizedResultCount() > baseline.unauthorizedResultCount()
                || (before > 0 && (after < 0 || after > before));
        return new MemoryRecallQualityComparisonReport.CaseChange(
                candidate.caseId(), before, after, regressed);
    }

    private static MemoryRecallQualityComparisonReport.Conclusion conclusion(
            MemoryRecallQualityEvalReport baseline,
            MemoryRecallQualityEvalReport candidate,
            MemoryRecallQualityComparisonReport.Metrics metrics) {
        if (!candidate.metrics().privacyGatePassed()
                || metrics.unauthorizedResultCountDelta() > 0
                || metrics.rankRegressionCount() > 0
                || metrics.expectedTopKHitRateDelta() < 0
                || metrics.firstResultHitRateDelta() < 0) {
            return MemoryRecallQualityComparisonReport.Conclusion.REGRESSION;
        }
        if (metrics.expectedTopKHitRateDelta() > 0 || metrics.firstResultHitRateDelta() > 0) {
            return MemoryRecallQualityComparisonReport.Conclusion.IMPROVED;
        }
        return MemoryRecallQualityComparisonReport.Conclusion.NO_CHANGE;
    }

    private static List<MemoryRecallQualityEvalCase> safeCases(List<MemoryRecallQualityEvalCase> cases) {
        return cases == null ? List.of() : List.copyOf(cases);
    }

    private static Map<String, MemoryRecallQualityEvalResult> byCaseId(
            List<MemoryRecallQualityEvalResult> results) {
        Map<String, MemoryRecallQualityEvalResult> byId = new LinkedHashMap<>();
        for (MemoryRecallQualityEvalResult result : results) {
            byId.put(result.caseId(), result);
        }
        return byId;
    }

    private static int rank(List<String> actualSourceIds, String expectedSourceId) {
        int index = actualSourceIds.indexOf(expectedSourceId);
        return index < 0 ? -1 : index + 1;
    }

    private static double rate(int numerator, int denominator) {
        return denominator == 0 ? 1.0d : (double) numerator / denominator;
    }

    private static double delta(double candidate, double baseline) {
        return Math.round((candidate - baseline) * 10_000.0d) / 10_000.0d;
    }

    private static String version(MemoryRecallQualityEvalReport report) {
        return report == null ? null : report.algorithmVersion();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
