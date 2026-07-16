package com.familyagent.module.memory.service;

import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.infra.ai.dto.EmbeddingRequest;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.module.memory.constant.MemoryEmbeddingErrorCode;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizedMemoryRecallEmbeddingService {

    static final int EMBEDDING_DIMENSIONS = 1536;

    private final AIServiceClient aiServiceClient;

    public RecallQueryEmbedding embed(Long familyId, String query) {
        long startedAt = System.nanoTime();
        EmbeddingResponse response;
        try {
            response = aiServiceClient.embedText(EmbeddingRequest.builder()
                    .text(query)
                    .dimensions(EMBEDDING_DIMENSIONS)
                    .sourceType("RECALL_QUERY")
                    .familyId(familyId)
                    .build());
        } catch (RuntimeException error) {
            log.warn("Recall query embedding failed, using text fallback: errorType={}",
                    error.getClass().getSimpleName());
            return RecallQueryEmbedding.failed(failedObservation(
                    null,
                    elapsedMillis(startedAt),
                    MemoryEmbeddingErrorCode.UNAVAILABLE.code()));
        }

        long latencyMs = response != null && response.getLatencyMs() != null
                ? Math.max(0, response.getLatencyMs())
                : elapsedMillis(startedAt);
        EmbeddingCallObservation observation = validateObservation(response, latencyMs);
        if (!observation.success()) {
            return RecallQueryEmbedding.failed(observation);
        }
        return new RecallQueryEmbedding(response.getEmbedding(), observation);
    }

    private static EmbeddingCallObservation validateObservation(EmbeddingResponse response, long latencyMs) {
        if (response == null || !response.isSuccess()) {
            return failedObservation(
                    response,
                    latencyMs,
                    responseErrorCode(response, MemoryEmbeddingErrorCode.UNAVAILABLE.code()));
        }
        if (response.isDegraded()) {
            return failedObservation(
                    response,
                    latencyMs,
                    responseErrorCode(response, MemoryEmbeddingErrorCode.DEGRADED.code()));
        }
        List<Double> values = response.getEmbedding();
        if (values == null || values.isEmpty()) {
            return failedObservation(response, latencyMs, MemoryEmbeddingErrorCode.EMPTY_RESPONSE.code());
        }
        if (values.size() != EMBEDDING_DIMENSIONS) {
            return failedObservation(response, latencyMs, MemoryEmbeddingErrorCode.DIMENSION_MISMATCH.code());
        }
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) {
                return failedObservation(response, latencyMs, MemoryEmbeddingErrorCode.NON_FINITE_VALUE.code());
            }
        }
        return new EmbeddingCallObservation(
                true,
                true,
                false,
                response.getProvider(),
                response.getModel(),
                values.size(),
                latencyMs,
                null);
    }

    private static EmbeddingCallObservation failedObservation(
            EmbeddingResponse response,
            long latencyMs,
            String errorCode) {
        return new EmbeddingCallObservation(
                true,
                false,
                true,
                response != null ? response.getProvider() : null,
                response != null ? response.getModel() : null,
                response != null ? response.getDimensions() : null,
                latencyMs,
                errorCode);
    }

    private static String responseErrorCode(EmbeddingResponse response, String fallback) {
        return response == null || response.getErrorCode() == null || response.getErrorCode().isBlank()
                ? fallback
                : response.getErrorCode().trim();
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    public record RecallQueryEmbedding(
            List<Double> values,
            EmbeddingCallObservation observation) {

        public RecallQueryEmbedding {
            values = values == null ? List.of() : List.copyOf(values);
        }

        private static RecallQueryEmbedding failed(EmbeddingCallObservation observation) {
            return new RecallQueryEmbedding(List.of(), observation);
        }
    }
}
