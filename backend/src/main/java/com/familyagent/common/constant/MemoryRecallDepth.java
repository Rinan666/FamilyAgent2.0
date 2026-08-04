package com.familyagent.common.constant;

public enum MemoryRecallDepth {
    NONE(0, 0),
    BRIEF(4, 2000),
    STANDARD(8, 5000),
    DEEP(16, 9000);

    private static final int CANDIDATE_MULTIPLIER = 5;
    private static final int MAX_RESULT_LIMIT = 20;

    private final int resultLimit;
    private final int contextCharBudget;

    MemoryRecallDepth(int resultLimit, int contextCharBudget) {
        this.resultLimit = resultLimit;
        this.contextCharBudget = contextCharBudget;
    }

    public int resultLimit() {
        return Math.min(resultLimit, MAX_RESULT_LIMIT);
    }

    public int candidateLimit() {
        return Math.min(resultLimit() * CANDIDATE_MULTIPLIER, MAX_RESULT_LIMIT * CANDIDATE_MULTIPLIER);
    }

    public int contextCharBudget() {
        return contextCharBudget;
    }
}
