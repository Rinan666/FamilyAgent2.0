package com.familyagent.module.memory.service;

import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.memory.dto.RebuildEmbeddingResponse;
import com.familyagent.module.memory.entity.MemoryEntry;
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
    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private FamilyMembershipFacade familyMembershipFacade;
    @Mock private MemoryIndexRebuildService indexRebuildService;

    private final EmbeddingAsyncProcessor asyncProcessor = new EmbeddingAsyncProcessor();

    @Test
    void rebuildFamilyIndexesDelegatesToIndexRebuildService() {
        RebuildEmbeddingResponse response = RebuildEmbeddingResponse.builder().familyId(10L).indexedCount(3).build();
        when(indexRebuildService.rebuildFamilyIndexes(10L, 500)).thenReturn(response);

        assertEquals(response, service().rebuildFamilyIndexes(10L, 500));
        verify(indexRebuildService).rebuildFamilyIndexes(10L, 500);
    }

    @Test
    void rebuildFamilyEmbeddingsUsesAllUnifiedEntries() {
        MemoryEntry diary = entry(101L, "DIARY");
        MemoryEntry memory = entry(2L, null);
        MemoryEntry growth = entry(103L, "GROWTH");
        when(memoryRepository.findActiveFamilyEntriesForIndexing(10L, 200))
                .thenReturn(List.of(diary, memory, growth));
        when(memoryRepository.selectById(101L)).thenReturn(diary);
        when(memoryRepository.selectById(2L)).thenReturn(memory);
        when(memoryRepository.selectById(103L)).thenReturn(growth);
        when(embeddingWriteRepository.upsertPending(any(), anyLong(), any(), anyLong(), any()))
                .thenReturn(1L);
        when(aiServiceClient.embedText(any())).thenReturn(embeddingResponse(
                true, false, Collections.nCopies(1536, 0.1), "model"));

        RebuildEmbeddingResponse result = service().rebuildFamilyEmbeddings(10L, 0);

        assertEquals(1, result.getDiaryCount());
        assertEquals(1, result.getMemoryCount());
        assertEquals(1, result.getGrowthRecordCount());
        assertEquals(3, result.getScheduledCount());
    }

    @Test
    void indexRejectsDegradedEmbeddingResponse() throws Exception {
        stubPendingEmbedding();
        when(aiServiceClient.embedText(any())).thenReturn(embeddingResponse(true, true, List.of(0.1), "model"));

        invokeIndex(service());

        verify(embeddingWriteRepository).markFailed(123L, "embedding degraded");
        verify(embeddingWriteRepository, never()).markReady(anyLong(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void indexRejectsDimensionMismatchAndNonFiniteValues() throws Exception {
        stubPendingEmbedding();
        when(aiServiceClient.embedText(any())).thenReturn(embeddingResponse(true, false, List.of(0.1, 0.2), "model"));
        invokeIndex(service());
        verify(embeddingWriteRepository).markFailed(123L, "embedding dimension mismatch: 2");

        List<Double> values = new ArrayList<>(Collections.nCopies(1536, 0.1));
        values.set(0, Double.NaN);
        when(aiServiceClient.embedText(any())).thenReturn(embeddingResponse(true, false, values, "model"));
        invokeIndex(service());
        verify(embeddingWriteRepository).markFailed(123L, "embedding contains non-finite values");
    }

    @Test
    void indexPersistsValidatedEmbeddingAsReady() throws Exception {
        List<Double> values = Collections.nCopies(1536, 0.1);
        stubPendingEmbedding();
        EmbeddingResponse response = embeddingResponse(true, false, values, "model");
        response.setProvider("provider");
        response.setPrivacyCategories(List.of("family"));
        when(aiServiceClient.embedText(any())).thenReturn(response);

        invokeIndex(service());

        verify(embeddingWriteRepository).upsertPending(
                eq("MEMORY"), eq(44L), eq(11L), eq(34L), org.mockito.ArgumentMatchers.any());
        verify(embeddingWriteRepository).markReady(123L, "model", values, List.of("family"), "provider", 1536);
    }

    @Test
    void deleteMemoryIndexUsesUnifiedSource() {
        service().deleteMemoryIndexAfterCommit(44L);
        verify(embeddingWriteRepository).deleteBySource("MEMORY", 44L);
    }

    @Test
    void delayedIndexTaskDoesNotRecreateArchivedEmbedding() {
        MemoryEntry archived = entry(44L, null);
        archived.setStatus("ARCHIVED");
        when(memoryRepository.selectById(44L)).thenReturn(archived);

        MemoryEntry activeSnapshot = entry(44L, null);
        service().indexMemoryAfterCommit(activeSnapshot);

        verify(embeddingWriteRepository).deleteBySource("MEMORY", 44L);
        verify(embeddingWriteRepository, never()).upsertPending(any(), anyLong(), any(), anyLong(), any());
    }

    private void stubPendingEmbedding() {
        when(embeddingWriteRepository.upsertPending(any(), anyLong(), any(), anyLong(), any())).thenReturn(123L);
    }

    private MemoryEmbeddingService service() {
        return new MemoryEmbeddingService(
                aiServiceClient,
                embeddingWriteRepository,
                memoryRepository,
                familyMembershipFacade,
                asyncProcessor,
                indexRebuildService);
    }

    private static void invokeIndex(MemoryEmbeddingService service) throws Exception {
        Method index = MemoryEmbeddingService.class.getDeclaredMethod(
                "index", Long.class, Long.class, Long.class, String.class);
        index.setAccessible(true);
        index.invoke(service, 44L, 11L, 34L, "manual memory text");
    }

    private static MemoryEntry entry(Long id, String originType) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setFamilyId(10L);
        entry.setUserId(34L);
        entry.setLibraryKind("FAMILY");
        entry.setOriginType(originType);
        entry.setStatus("ACTIVE");
        entry.setContent("content " + id);
        return entry;
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
