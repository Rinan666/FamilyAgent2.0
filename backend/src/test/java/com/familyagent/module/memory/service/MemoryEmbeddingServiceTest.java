package com.familyagent.module.memory.service;

import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.module.diary.facade.MemoryIndexDiaryFacade;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.growth.facade.MemoryIndexGrowthFacade;
import com.familyagent.module.memory.dto.RebuildEmbeddingResponse;
import com.familyagent.module.memory.repository.MemoryEmbeddingWriteRepository;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryEmbeddingServiceTest {

    @Mock private AIServiceClient aiServiceClient;
    @Mock private MemoryEmbeddingWriteRepository embeddingWriteRepository;
    @Mock private MemoryIndexDiaryFacade diaryRepository;
    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private MemoryIndexGrowthFacade growthRecordRepository;
    @Mock private FamilyMembershipFacade familyMembershipFacade;
    @Mock private MemoryIndexRebuildService indexRebuildService;

    // In a unit test without Spring proxies, @Async methods execute synchronously.
    private final EmbeddingAsyncProcessor asyncProcessor = new EmbeddingAsyncProcessor();

    @Test
    void rebuildFamilyIndexes_shouldDelegateToIndexRebuildService() {
        RebuildEmbeddingResponse response = RebuildEmbeddingResponse.builder()
                .familyId(10L)
                .indexedCount(3)
                .build();
        when(indexRebuildService.rebuildFamilyIndexes(10L, 500)).thenReturn(response);

        RebuildEmbeddingResponse result = service().rebuildFamilyIndexes(10L, 500);

        assertEquals(response, result);
        verify(indexRebuildService).rebuildFamilyIndexes(10L, 500);
    }

    @Test
    void index_shouldRejectDegradedEmbeddingResponse() throws Exception {
        stubPendingEmbedding();
        when(aiServiceClient.embedText(any())).thenReturn(embeddingResponse(
                true,
                true,
                List.of(0.1, 0.2),
                "test-model"));

        invokeIndex(service());

        verify(embeddingWriteRepository).markFailed(123L, "embedding degraded");
        verify(embeddingWriteRepository, never()).markReady(anyLong(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void index_shouldRejectEmbeddingDimensionMismatch() throws Exception {
        stubPendingEmbedding();
        when(aiServiceClient.embedText(any())).thenReturn(embeddingResponse(
                true,
                false,
                List.of(0.1, 0.2),
                "test-model"));

        invokeIndex(service());

        verify(embeddingWriteRepository).markFailed(123L, "embedding dimension mismatch: 2");
        verify(embeddingWriteRepository, never()).markReady(anyLong(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void index_shouldRejectReportedEmbeddingDimensionMismatch() throws Exception {
        stubPendingEmbedding();
        EmbeddingResponse response = embeddingResponse(
                true,
                false,
                Collections.nCopies(1536, 0.1),
                "test-model");
        response.setDimensions(128);
        when(aiServiceClient.embedText(any())).thenReturn(response);

        invokeIndex(service());

        verify(embeddingWriteRepository).markFailed(123L, "embedding reported dimension mismatch: 128");
        verify(embeddingWriteRepository, never()).markReady(anyLong(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void index_shouldRejectNonFiniteEmbeddingValues() throws Exception {
        List<Double> values = new ArrayList<>(Collections.nCopies(1536, 0.1));
        values.set(0, Double.NaN);
        stubPendingEmbedding();
        when(aiServiceClient.embedText(any())).thenReturn(embeddingResponse(
                true,
                false,
                values,
                "test-model"));

        invokeIndex(service());

        verify(embeddingWriteRepository).markFailed(123L, "embedding contains non-finite values");
        verify(embeddingWriteRepository, never()).markReady(anyLong(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void index_shouldRejectZeroEmbeddingVector() throws Exception {
        stubPendingEmbedding();
        when(aiServiceClient.embedText(any())).thenReturn(embeddingResponse(
                true,
                false,
                Collections.nCopies(1536, 0.0),
                "test-model"));

        invokeIndex(service());

        verify(embeddingWriteRepository).markFailed(123L, "embedding vector is zero");
        verify(embeddingWriteRepository, never()).markReady(anyLong(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void index_shouldPersistValidatedEmbeddingAsReady() throws Exception {
        List<Double> values = Collections.nCopies(1536, 0.1);
        stubPendingEmbedding();
        EmbeddingResponse response = embeddingResponse(true, false, values, "test-model");
        response.setProvider("test-provider");
        response.setPrivacyCategories(List.of("family"));
        when(aiServiceClient.embedText(any())).thenReturn(response);

        invokeIndex(service());

        verify(embeddingWriteRepository).markReady(
                123L,
                "test-model",
                values,
                List.of("family"),
                "test-provider",
                1536);
        verify(embeddingWriteRepository, never()).markFailed(anyLong(), any());
    }

    @Test
    void index_shouldPersistGenericFailureWhenProviderThrows() throws Exception {
        stubPendingEmbedding();
        when(aiServiceClient.embedText(any())).thenThrow(new IllegalStateException("private provider detail"));

        invokeIndex(service());

        verify(embeddingWriteRepository).markFailed(123L, "embedding indexing failed");
        verify(embeddingWriteRepository, never()).markReady(anyLong(), any(), any(), any(), any(), any(Integer.class));
    }

    private void stubPendingEmbedding() {
        when(embeddingWriteRepository.upsertPending(any(), anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(123L);
    }

    private MemoryEmbeddingService service() {
        return new MemoryEmbeddingService(
                aiServiceClient,
                embeddingWriteRepository,
                diaryRepository,
                memoryRepository,
                growthRecordRepository,
                familyMembershipFacade,
                asyncProcessor,
                indexRebuildService);
    }

    private static void invokeIndex(MemoryEmbeddingService service) throws Exception {
        Method index = MemoryEmbeddingService.class.getDeclaredMethod(
                "index", String.class, Long.class, Long.class, Long.class, String.class);
        index.setAccessible(true);
        index.invoke(service, "DIARY", 44L, 11L, 34L, "manual diary text");
    }

    private static EmbeddingResponse embeddingResponse(
            boolean success,
            boolean degraded,
            List<Double> embedding,
            String model) {
        EmbeddingResponse response = new EmbeddingResponse();
        response.setSuccess(success);
        response.setDegraded(degraded);
        response.setEmbedding(embedding);
        response.setModel(model);
        response.setDimensions(embedding == null ? null : embedding.size());
        response.setPrivacyCategories(List.of());
        return response;
    }
}
