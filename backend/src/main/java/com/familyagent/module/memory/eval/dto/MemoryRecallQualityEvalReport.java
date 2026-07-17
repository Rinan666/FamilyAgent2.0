package com.familyagent.module.memory.eval.dto;

import java.util.List;

public record MemoryRecallQualityEvalReport(
        String schemaVersion,
        String suiteVersion,
        String algorithmVersion,
        Metrics metrics,
        List<MemoryRecallQualityEvalResult> results) {

    public MemoryRecallQualityEvalReport {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public record Metrics(
            int caseCount,
            int passedCount,
            int expectedSourceCount,
            int expectedHitCount,
            double expectedTopKHitRate,
            int firstResultHitCount,
            double firstResultHitRate,
            int unauthorizedResultCount,
            boolean privacyGatePassed) {
    }
}
