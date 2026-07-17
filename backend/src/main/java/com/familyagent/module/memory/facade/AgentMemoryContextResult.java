package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.dto.RecallSourceSummary;

import java.util.List;

public record AgentMemoryContextResult(
        String context,
        AgentMemoryContextMetadata metadata,
        boolean success,
        String errorCode,
        EmbeddingCallObservation embeddingObservation) {

    public AgentMemoryContextResult {
        context = context == null ? "" : context;
        metadata = metadata == null ? AgentMemoryContextMetadata.empty() : metadata;
        errorCode = errorCode == null || errorCode.isBlank() ? null : errorCode.trim();
    }

    public AgentMemoryContextResult(String context, AgentMemoryContextMetadata metadata) {
        this(context, metadata, true, null, null);
    }

    public AgentMemoryContextResult(
            String context,
            AgentMemoryContextMetadata metadata,
            boolean success,
            String errorCode) {
        this(context, metadata, success, errorCode, null);
    }

    public static AgentMemoryContextResult empty() {
        return new AgentMemoryContextResult("", AgentMemoryContextMetadata.empty());
    }

    public static AgentMemoryContextResult failed(AgentMemoryContextErrorCode errorCode) {
        return new AgentMemoryContextResult("", AgentMemoryContextMetadata.empty(), false, errorCode.code(), null);
    }

    public static AgentMemoryContextResult fromRecall(String context, AuthorizedMemoryRecallResult recall) {
        if (recall == null) {
            return new AgentMemoryContextResult(context, AgentMemoryContextMetadata.empty());
        }

        AgentMemoryRagMetadata rag = new AgentMemoryRagMetadata(
                recall.getRetrievalMode(),
                recall.getEmbeddingReadyCount(),
                recall.getDiaryCount(),
                recall.getMemoryCount(),
                recall.getGrowthRecordCount(),
                0,
                0,
                recall.getDiaryCount() + recall.getMemoryCount() + recall.getGrowthRecordCount(),
                mapSources(recall.getSources()));
        return new AgentMemoryContextResult(
                context,
                new AgentMemoryContextMetadata(rag, recall.getQuery()),
                true,
                null,
                recall.getEmbeddingObservation());
    }

    private static List<AgentMemoryRecallSource> mapSources(List<RecallSourceSummary> sources) {
        if (sources == null) {
            return List.of();
        }
        return sources.stream()
                .map(source -> new AgentMemoryRecallSource(
                        source.getId(),
                        source.getSourceType(),
                        source.getTitle(),
                        source.getSnippet(),
                        source.getVisibility(),
                        source.getTemporalLayer(),
                        source.getTopics(),
                        source.getScenes()))
                .toList();
    }

}
