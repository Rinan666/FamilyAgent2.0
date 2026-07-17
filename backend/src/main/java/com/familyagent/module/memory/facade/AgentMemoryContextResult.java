package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.dto.MemoryRecallContextMetadata;
import com.familyagent.module.memory.dto.MemoryRecallRagMetadata;

import java.util.List;

public record AgentMemoryContextResult(
        String context,
        MemoryRecallContextMetadata metadata,
        boolean success,
        String errorCode,
        EmbeddingCallObservation embeddingObservation) {

    public AgentMemoryContextResult {
        context = context == null ? "" : context;
        metadata = metadata == null ? MemoryRecallContextMetadata.empty() : metadata;
        errorCode = errorCode == null || errorCode.isBlank() ? null : errorCode.trim();
    }

    public AgentMemoryContextResult(String context, MemoryRecallContextMetadata metadata) {
        this(context, metadata, true, null, null);
    }

    public AgentMemoryContextResult(
            String context,
            MemoryRecallContextMetadata metadata,
            boolean success,
            String errorCode) {
        this(context, metadata, success, errorCode, null);
    }

    public static AgentMemoryContextResult empty() {
        return new AgentMemoryContextResult("", MemoryRecallContextMetadata.empty());
    }

    public static AgentMemoryContextResult failed(AgentMemoryContextErrorCode errorCode) {
        return new AgentMemoryContextResult("", MemoryRecallContextMetadata.empty(), false, errorCode.code(), null);
    }

    public static AgentMemoryContextResult fromRecall(String context, AuthorizedMemoryRecallResult recall) {
        if (recall == null) {
            return new AgentMemoryContextResult(context, MemoryRecallContextMetadata.empty());
        }

        MemoryRecallRagMetadata rag = new MemoryRecallRagMetadata(
                recall.getRetrievalMode(),
                recall.getEmbeddingReadyCount(),
                recall.getDiaryCount(),
                recall.getMemoryCount(),
                recall.getGrowthRecordCount(),
                0,
                0,
                recall.getDiaryCount() + recall.getMemoryCount() + recall.getGrowthRecordCount(),
                recall.getSources() == null ? List.of() : recall.getSources());
        return new AgentMemoryContextResult(
                context,
                new MemoryRecallContextMetadata(rag, recall.getQuery()),
                true,
                null,
                recall.getEmbeddingObservation());
    }

}
