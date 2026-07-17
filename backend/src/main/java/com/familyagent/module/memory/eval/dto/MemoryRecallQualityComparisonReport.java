package com.familyagent.module.memory.eval.dto;

import java.util.List;

public record MemoryRecallQualityComparisonReport(
        String schemaVersion,
        String baselineAlgorithmVersion,
        String candidateAlgorithmVersion,
        Conclusion conclusion,
        Metrics metrics,
        List<CaseChange> caseChanges) {

    public MemoryRecallQualityComparisonReport {
        caseChanges = caseChanges == null ? List.of() : List.copyOf(caseChanges);
    }

    public enum Conclusion {
        IMPROVED,
        NO_CHANGE,
        REGRESSION,
        INCOMPARABLE
    }

    public record Metrics(
            double expectedTopKHitRateDelta,
            double firstResultHitRateDelta,
            int unauthorizedResultCountDelta,
            int rankRegressionCount) {
    }

    public record CaseChange(
            String caseId,
            int baselineBestRank,
            int candidateBestRank,
            boolean regressed) {
    }
}
