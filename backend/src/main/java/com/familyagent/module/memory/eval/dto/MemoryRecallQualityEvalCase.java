package com.familyagent.module.memory.eval.dto;

import java.util.List;

public record MemoryRecallQualityEvalCase(
        String caseId,
        int candidateCount,
        List<String> expectedSourceIds,
        List<String> unauthorizedSourceIds,
        List<String> actualSourceIds) {

    public MemoryRecallQualityEvalCase {
        expectedSourceIds = safeCopy(expectedSourceIds);
        unauthorizedSourceIds = safeCopy(unauthorizedSourceIds);
        actualSourceIds = safeCopy(actualSourceIds);
    }

    private static List<String> safeCopy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
