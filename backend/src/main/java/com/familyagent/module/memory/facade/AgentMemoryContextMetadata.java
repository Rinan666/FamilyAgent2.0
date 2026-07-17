package com.familyagent.module.memory.facade;

public record AgentMemoryContextMetadata(
        AgentMemoryRagMetadata rag,
        String retrievalQuery) {

    public AgentMemoryContextMetadata {
        retrievalQuery = retrievalQuery == null || retrievalQuery.isBlank()
                ? null
                : retrievalQuery.trim();
    }

    public static AgentMemoryContextMetadata empty() {
        return new AgentMemoryContextMetadata(null, null);
    }

    public boolean isEmpty() {
        return rag == null && retrievalQuery == null;
    }
}
