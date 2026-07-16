package com.familyagent.module.memory.facade;

public enum AgentMemoryContextErrorCode {
    RECALL_FAILED("MEMORY_RECALL_FAILED");

    private final String code;

    AgentMemoryContextErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
