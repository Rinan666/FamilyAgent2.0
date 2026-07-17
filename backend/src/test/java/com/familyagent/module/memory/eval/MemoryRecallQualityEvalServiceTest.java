package com.familyagent.module.memory.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.module.memory.eval.dto.MemoryRecallQualityComparisonReport;
import com.familyagent.module.memory.eval.dto.MemoryRecallQualityEvalCase;
import com.familyagent.module.memory.eval.dto.MemoryRecallQualityEvalReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRecallQualityEvalServiceTest {

    private static final String SUITE_VERSION = "memory.recall.synthetic.v1";
    private final MemoryRecallQualityEvalService service = new MemoryRecallQualityEvalService();

    @Test
    void evaluateReportsQualityAndPrivacyMetricsWithoutSourceContent() throws Exception {
        MemoryRecallQualityEvalReport report = service.evaluate(
                SUITE_VERSION,
                "ranking.v1",
                List.of(
                        new MemoryRecallQualityEvalCase(
                                "exact-keyword",
                                4,
                                List.of("memory-2"),
                                List.of("memory-99"),
                                List.of("memory-2", "memory-1")),
                        new MemoryRecallQualityEvalCase(
                                "semantic-topic",
                                3,
                                List.of("diary-7"),
                                List.of(),
                                List.of("memory-4", "diary-7"))));

        assertEquals("memory.recall.quality.report.v1", report.schemaVersion());
        assertEquals(2, report.metrics().caseCount());
        assertEquals(2, report.metrics().passedCount());
        assertEquals(1.0d, report.metrics().expectedTopKHitRate());
        assertEquals(0.5d, report.metrics().firstResultHitRate());
        assertEquals(0, report.metrics().unauthorizedResultCount());
        assertTrue(report.metrics().privacyGatePassed());

        String json = new ObjectMapper().writeValueAsString(report);
        assertFalse(json.contains("private query text"));
        assertFalse(json.contains("private memory body"));
        assertFalse(json.contains("actualSourceIds"));
        assertFalse(json.contains("expectedSourceIds"));
        assertFalse(json.contains("unauthorizedSourceIds"));
    }

    @Test
    void compareTreatsUnauthorizedResultAsHardRegression() {
        MemoryRecallQualityEvalReport baseline = service.evaluate(
                SUITE_VERSION,
                "ranking.v1",
                List.of(caseWithActual(List.of("memory-1"))));
        MemoryRecallQualityEvalReport candidate = service.evaluate(
                SUITE_VERSION,
                "ranking.v2",
                List.of(caseWithActual(List.of("memory-99", "memory-1"))));

        MemoryRecallQualityComparisonReport comparison = service.compare(baseline, candidate);

        assertEquals(MemoryRecallQualityComparisonReport.Conclusion.REGRESSION, comparison.conclusion());
        assertEquals(1, comparison.metrics().unauthorizedResultCountDelta());
        assertFalse(candidate.metrics().privacyGatePassed());
    }

    private static MemoryRecallQualityEvalCase caseWithActual(List<String> actualSourceIds) {
        return new MemoryRecallQualityEvalCase(
                "privacy-exclusion",
                2,
                List.of("memory-1"),
                List.of("memory-99"),
                actualSourceIds);
    }
}
