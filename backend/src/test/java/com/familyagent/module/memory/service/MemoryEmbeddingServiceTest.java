package com.familyagent.module.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryEmbeddingServiceTest {

    @Mock private AIServiceClient aiServiceClient;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private DiaryEntryRepository diaryRepository;
    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private GrowthGuardRecordRepository growthRecordRepository;
    @Mock private FamilyService familyService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    // Use a real EmbeddingAsyncProcessor — in test context @Async is a no-op so tasks run synchronously.
    private final EmbeddingAsyncProcessor asyncProcessor = new EmbeddingAsyncProcessor();

    @Test
    void upsertPending_shouldSupersedeOlderPendingEmbeddingsForSameSource() throws Exception {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(123L);

        MemoryEmbeddingService service = new MemoryEmbeddingService(
                aiServiceClient,
                jdbcTemplate,
                objectMapper,
                diaryRepository,
                memoryRepository,
                growthRecordRepository,
                familyService,
                asyncProcessor);

        Method upsertPending = MemoryEmbeddingService.class.getDeclaredMethod(
                "upsertPending", String.class, Long.class, Long.class, Long.class, String.class);
        upsertPending.setAccessible(true);
        Object result = upsertPending.invoke(service, "DIARY", 44L, 11L, 34L, "new-hash");

        assertEquals(123L, result);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> arg1Captor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> arg2Captor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> arg3Captor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> arg4Captor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), arg1Captor.capture(), arg2Captor.capture(), arg3Captor.capture(), arg4Captor.capture());

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("content_hash <> ?"));
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(
                mapper.readTree("{\"error\":\"Superseded by newer embedding request\",\"cleanupReason\":\"STALE_PENDING_SUPERSEDED\",\"supersededByContentHash\":\"new-hash\"}"),
                mapper.readTree(String.valueOf(arg1Captor.getValue())));
        assertEquals("DIARY", arg2Captor.getValue());
        assertEquals(44L, arg3Captor.getValue());
        assertEquals("new-hash", arg4Captor.getValue());
    }

    @Test
    void index_shouldRejectDegradedEmbeddingResponse() throws Exception {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(123L);
        when(aiServiceClient.embedText(any())).thenReturn(embeddingResponse(
                true,
                true,
                List.of(0.1, 0.2),
                "test-model"));

        MemoryEmbeddingService service = new MemoryEmbeddingService(
                aiServiceClient,
                jdbcTemplate,
                objectMapper,
                diaryRepository,
                memoryRepository,
                growthRecordRepository,
                familyService,
                asyncProcessor);

        Method index = MemoryEmbeddingService.class.getDeclaredMethod(
                "index", String.class, Long.class, Long.class, Long.class, String.class);
        index.setAccessible(true);
        index.invoke(service, "DIARY", 44L, 11L, 34L, "manual diary text");

        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("SET status = 'FAILED'"),
                org.mockito.ArgumentMatchers.any(),
                eq(123L));
    }

    @Test
    void index_shouldRejectEmbeddingDimensionMismatch() throws Exception {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(123L);
        when(aiServiceClient.embedText(any())).thenReturn(embeddingResponse(
                true,
                false,
                List.of(0.1, 0.2),
                "test-model"));

        MemoryEmbeddingService service = new MemoryEmbeddingService(
                aiServiceClient,
                jdbcTemplate,
                objectMapper,
                diaryRepository,
                memoryRepository,
                growthRecordRepository,
                familyService,
                asyncProcessor);

        Method index = MemoryEmbeddingService.class.getDeclaredMethod(
                "index", String.class, Long.class, Long.class, Long.class, String.class);
        index.setAccessible(true);
        index.invoke(service, "DIARY", 44L, 11L, 34L, "manual diary text");

        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("SET status = 'FAILED'"),
                org.mockito.ArgumentMatchers.any(),
                eq(123L));
    }

    @Test
    void index_shouldRejectNonFiniteEmbeddingValues() throws Exception {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(123L);
        when(aiServiceClient.embedText(any())).thenReturn(embeddingResponse(
                true,
                false,
                List.of(0.1, Double.NaN),
                "test-model"));

        MemoryEmbeddingService service = new MemoryEmbeddingService(
                aiServiceClient,
                jdbcTemplate,
                objectMapper,
                diaryRepository,
                memoryRepository,
                growthRecordRepository,
                familyService,
                asyncProcessor);

        Method index = MemoryEmbeddingService.class.getDeclaredMethod(
                "index", String.class, Long.class, Long.class, Long.class, String.class);
        index.setAccessible(true);
        index.invoke(service, "DIARY", 44L, 11L, 34L, "manual diary text");

        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("SET status = 'FAILED'"),
                org.mockito.ArgumentMatchers.any(),
                eq(123L));
    }

    private static EmbeddingResponse embeddingResponse(boolean success, boolean degraded, List<Double> embedding, String model) {
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
