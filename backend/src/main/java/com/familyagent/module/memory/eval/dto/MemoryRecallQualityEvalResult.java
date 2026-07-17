package com.familyagent.module.memory.eval.dto;

import java.util.List;

public record MemoryRecallQualityEvalResult(
        String caseId,
        int candidateCount,
        int expectedSourceCount,
        List<Integer> expectedRanks,
        boolean topKHit,
        boolean firstResultHit,
        int unauthorizedResultCount,
        boolean passed) {

    public MemoryRecallQualityEvalResult {
        expectedRanks = expectedRanks == null ? List.of() : List.copyOf(expectedRanks);
    }

    public int bestExpectedRank() {
        return expectedRanks.stream()
                .filter(rank -> rank > 0)
                .min(Integer::compareTo)
                .orElse(-1);
    }
}
