package com.familyagent.module.agent.dto;

import com.familyagent.module.memory.facade.AgentMemoryContextMetadata;
import com.familyagent.module.memory.facade.AgentMemoryRagMetadata;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentChatMetadataEvent(
        String type,
        AgentMemoryRagMetadata rag,
        String retrievalQuery,
        String effectiveContext,
        Long targetUserId,
        Long targetPersonaId,
        String targetLabel,
        String answerDepth,
        String recallDepth,
        String webSearchPolicy,
        Boolean decisionSupport,
        Boolean intentDegraded,
        Boolean contextChanged,
        Integer sourceCount,
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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                safeMetadata.rag() == null ? 0 : safeMetadata.rag().totalReferenceCount(),
                requestId,
                runId);
    }

    public static AgentChatMetadataEvent create(
            AgentMemoryContextMetadata metadata,
            AgentIntentPlan intentPlan,
            String requestId,
            Long runId) {
        AgentMemoryContextMetadata safeMetadata = metadata == null
                ? AgentMemoryContextMetadata.empty()
                : metadata;
        return new AgentChatMetadataEvent(
                EVENT_TYPE,
                safeMetadata.rag(),
                safeMetadata.retrievalQuery(),
                intentPlan.contextType().name(),
                intentPlan.targetUserId(),
                intentPlan.targetPersonaId(),
                intentPlan.targetLabel(),
                intentPlan.responsePlan().answerDepth().name(),
                intentPlan.responsePlan().recallDepth().name(),
                intentPlan.responsePlan().webSearchPolicy().name(),
                intentPlan.responsePlan().decisionSupport(),
                intentPlan.responsePlan().degraded(),
                intentPlan.contextChanged(),
                safeMetadata.rag() == null ? 0 : safeMetadata.rag().totalReferenceCount(),
                requestId,
                runId);
    }
}
