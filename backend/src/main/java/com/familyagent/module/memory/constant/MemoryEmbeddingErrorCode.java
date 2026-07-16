package com.familyagent.module.memory.constant;

public enum MemoryEmbeddingErrorCode {
    UNAVAILABLE("AI_EMBEDDING_UNAVAILABLE"),
    DEGRADED("AI_EMBEDDING_DEGRADED"),
    EMPTY_RESPONSE("AI_EMBEDDING_EMPTY_RESPONSE"),
    DIMENSION_MISMATCH("AI_EMBEDDING_DIMENSION_MISMATCH"),
    NON_FINITE_VALUE("AI_EMBEDDING_NON_FINITE_VALUE");

    private final String code;

    MemoryEmbeddingErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
