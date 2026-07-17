package com.familyagent.module.agent.dto;

import com.familyagent.module.memory.facade.AgentMemoryContextMetadata;
import com.familyagent.module.memory.facade.AgentMemoryRagMetadata;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentChatMetadataEvent(
        String type,
        AgentMemoryRagMetadata rag,
        String retrievalQuery,
        String requestId,
        Long runId) {

    private static final String EVENT_TYPE = "metadata";

    public static AgentChatMetadataEvent create(
            AgentMemoryContextMetadata metadata,
            String requestId,
            Long runId) {
        AgentMemoryContextMetadata safeMetadata = metadata == null
                ? AgentMemoryContextMetadata.empty()
                : metadata;
        return new AgentChatMetadataEvent(
                EVENT_TYPE,
                safeMetadata.rag(),
                safeMetadata.retrievalQuery(),
                requestId,
                runId);
    }
}
