package com.familyagent.module.memory.service;

import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.infra.ai.dto.EmbeddingRequest;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.module.memory.constant.MemoryEmbeddingErrorCode;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizedMemoryRecallEmbeddingServiceTest {

    @Mock private AIServiceClient aiServiceClient;

    @Test
    void embed_returnsValidatedVectorAndTypedMetadata() {
        when(aiServiceClient.embedText(any())).thenReturn(response(
                true,
                false,
                Collections.nCopies(1536, 0.01),
                null));

        AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding result = service().embed(
                10L,
                "bedtime reminder");

        EmbeddingCallObservation observation = result.observation();
        assertEquals(1536, result.values().size());
        assertTrue(observation.attempted());
        assertTrue(observation.success());
        assertFalse(observation.degraded());
        assertEquals("local", observation.provider());
        assertEquals("local/hash-embedding", observation.model());
        assertEquals(1536, observation.dimensions());
        assertEquals(18L, observation.latencyMs());
        ArgumentCaptor<EmbeddingRequest> captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(aiServiceClient).embedText(captor.capture());
        assertEquals("RECALL_QUERY", captor.getValue().getSourceType());
        assertEquals(10L, captor.getValue().getFamilyId());
        assertEquals(1536, captor.getValue().getDimensions());
    }

    @Test
    void embed_recordsDegradedProviderResponseAsTextFallback() {
        when(aiServiceClient.embedText(any())).thenReturn(response(
                true,
                true,
                Collections.nCopies(1536, 0.01),
                "AI_PROVIDER_DEGRADED"));

        AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding result = service().embed(10L, "bedtime");

        assertTrue(result.values().isEmpty());
        assertFalse(result.observation().success());
        assertTrue(result.observation().degraded());
        assertEquals("AI_PROVIDER_DEGRADED", result.observation().errorCode());
    }

    @Test
    void embed_recordsDimensionMismatchWithoutReturningVector() {
        when(aiServiceClient.embedText(any())).thenReturn(response(
                true,
                false,
                List.of(0.1, 0.2),
                null));

        AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding result = service().embed(10L, "bedtime");

        assertTrue(result.values().isEmpty());
        assertFalse(result.observation().success());
        assertTrue(result.observation().degraded());
        assertEquals(MemoryEmbeddingErrorCode.DIMENSION_MISMATCH.code(), result.observation().errorCode());
        assertEquals(2, result.observation().dimensions());
    }

    @Test
    void embed_recordsTransportFailureWithoutLeakingQuery() {
        when(aiServiceClient.embedText(any())).thenThrow(new RuntimeException("private query detail"));

        AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding result = service().embed(
                10L,
                "private family query");

        assertTrue(result.values().isEmpty());
        assertFalse(result.observation().success());
        assertTrue(result.observation().degraded());
        assertEquals(MemoryEmbeddingErrorCode.UNAVAILABLE.code(), result.observation().errorCode());
    }

    private AuthorizedMemoryRecallEmbeddingService service() {
        return new AuthorizedMemoryRecallEmbeddingService(aiServiceClient);
    }

    private static EmbeddingResponse response(
            boolean success,
            boolean degraded,
            List<Double> embedding,
            String errorCode) {
        EmbeddingResponse response = new EmbeddingResponse();
        response.setSuccess(success);
        response.setDegraded(degraded);
        response.setProvider("local");
        response.setModel("local/hash-embedding");
        response.setEmbedding(embedding);
        response.setDimensions(embedding == null ? null : embedding.size());
        response.setLatencyMs(18L);
        response.setErrorCode(errorCode);
        return response;
    }
}
