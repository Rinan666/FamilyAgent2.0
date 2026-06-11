package com.familyagent.module.memory.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private MemoryEntryVoteRepository voteRepository;
    @Mock private FamilyService familyService;
    @Mock private MemoryEmbeddingService memoryEmbeddingService;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock familyCreateLock;

    @Test
    void createFamilyMemory_shouldRejectManualHeritageWithoutPositiveJudge() {
        MemoryService service = new MemoryService(
                memoryRepository,
                voteRepository,
                familyService,
                memoryEmbeddingService,
                redissonClient);
        CreateFamilyMemoryRequest request = requestWithMetadata(Map.of("source", "HERITAGE_ENTRY"));

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            BusinessException error = assertThrows(BusinessException.class, () -> service.createFamilyMemory(request));

            assertEquals("请先完成家族经验保存价值判断", error.getMessage());
        }

        verify(familyService).checkMembership(1L);
        verify(memoryRepository, never()).insert(any(MemoryEntry.class));
    }

    @Test
    void createFamilyMemory_shouldAllowManualHeritageWithPositiveJudge() throws InterruptedException {
        MemoryService service = serviceWithAcquiredLock();
        CreateFamilyMemoryRequest request = requestWithMetadata(Map.of(
                "source", "HERITAGE_INTERVIEW",
                "saveJudge", Map.of("shouldSave", true, "learningValueScore", 4)));
        when(memoryRepository.findActiveFamilyMemories(eq(1L), eq(10L), any(Integer.class))).thenReturn(List.of());

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            MemoryEntry result = service.createFamilyMemory(request);

            assertEquals("ELDER_ADVICE", result.getType());
        }

        ArgumentCaptor<MemoryEntry> captor = ArgumentCaptor.forClass(MemoryEntry.class);
        verify(memoryRepository).insert(captor.capture());
        Map<?, ?> savedMetadata = (Map<?, ?>) captor.getValue().getMetadata();
        assertTrue(((Map<?, ?>) savedMetadata.get("saveJudge")).containsKey("shouldSave"));
    }

    @Test
    void createFamilyMemory_shouldNotRequireJudgeForDiaryPromotion() throws InterruptedException {
        MemoryService service = serviceWithAcquiredLock();
        CreateFamilyMemoryRequest request = requestWithMetadata(Map.of("source", "DIARY_PROMOTION", "sourceDiaryId", 99));
        when(memoryRepository.findActiveBySourceDiaryId(1L, "99")).thenReturn(null);
        when(memoryRepository.findActiveFamilyMemories(eq(1L), eq(10L), any(Integer.class))).thenReturn(List.of());

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            service.createFamilyMemory(request);
        }

        verify(memoryRepository).insert(any(MemoryEntry.class));
    }

    @Test
    void searchFamilyMemories_shouldClampPageAndAttachVoteStats() {
        MemoryService service = new MemoryService(
                memoryRepository,
                voteRepository,
                familyService,
                memoryEmbeddingService,
                redissonClient);
        MemoryEntry entry = new MemoryEntry();
        entry.setId(301L);
        entry.setFamilyId(1L);
        entry.setUserId(22L);
        entry.setType("ELDER_ADVICE");
        entry.setScope("FAMILY_VISIBLE");
        entry.setStatus("ACTIVE");
        entry.setContent("坚持晨读");
        entry.setMetadata(Map.of());

        when(memoryRepository.countActiveFamilyMemoriesSearch(1L, 10L, 22L, "晨读")).thenReturn(9L);
        when(memoryRepository.searchActiveFamilyMemories(1L, 10L, 22L, "晨读", 6, 6L)).thenReturn(List.of(entry));
        when(voteRepository.statsByMemoryId(301L, 10L)).thenReturn(null);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            var result = service.searchFamilyMemories(1L, 22L, " 晨读 ", 4, 0);

            assertEquals(2L, result.getPage());
            assertEquals(6L, result.getPageSize());
            assertEquals(9L, result.getTotal());
            assertEquals(1, result.getItems().size());
            assertTrue(((Map<?, ?>) result.getItems().get(0).getMetadata()).containsKey("voteStats"));
        }
    }

    private MemoryService serviceWithAcquiredLock() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(familyCreateLock);
        when(familyCreateLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(familyCreateLock.isHeldByCurrentThread()).thenReturn(true);
        return new MemoryService(
                memoryRepository,
                voteRepository,
                familyService,
                memoryEmbeddingService,
                redissonClient);
    }

    private static CreateFamilyMemoryRequest requestWithMetadata(Map<String, Object> metadata) {
        CreateFamilyMemoryRequest request = new CreateFamilyMemoryRequest();
        request.setFamilyId(1L);
        request.setContent("爸爸当年选专业只看热门，后来转行代价很大，提醒后辈选择前先看长期适配。");
        request.setType("ELDER_ADVICE");
        request.setScope("FAMILY_VISIBLE");
        request.setSummary("选择前先看长期适配");
        request.setMetadata(metadata);
        return request;
    }
}
