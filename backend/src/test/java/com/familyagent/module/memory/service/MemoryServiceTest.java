package com.familyagent.module.memory.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.dto.WriteMemoryMetadata;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private MemoryEntryVoteRepository voteRepository;
    @Mock private FamilyMembershipFacade familyMembershipFacade;
    @Mock private MemoryEmbeddingService memoryEmbeddingService;
    @Mock private MemoryMergeService memoryMergeService;
    @Mock private MemorySearchService memorySearchService;
    @Mock private MemoryVoteService memoryVoteService;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock familyCreateLock;

    // --- createFamilyMemory ---

    @Test
    void createFamilyMemory_shouldAllowManualHeritageWithoutSaveJudge() throws InterruptedException {
        MemoryService service = serviceWithAcquiredLock();
        CreateFamilyMemoryRequest request = requestWithMetadata(Map.<String, Object>of(
                "source", "HERITAGE_INTERVIEW"));
        when(memoryMergeService.findSimilar(any(), any(), eq(10L), any(), any())).thenReturn(null);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            MemoryEntry result = service.createFamilyMemory(request);
            assertEquals("KNOWLEDGE", result.getType());
        }

        ArgumentCaptor<MemoryEntry> captor = ArgumentCaptor.forClass(MemoryEntry.class);
        verify(memoryRepository).insert(captor.capture());
        Map<?, ?> savedMetadata = (Map<?, ?>) captor.getValue().getMetadata();
        assertEquals("HERITAGE_INTERVIEW", savedMetadata.get("source"));
    }

    @Test
    void createFamilyMemory_shouldNotRequireJudgeForDiaryPromotion() throws InterruptedException {
        MemoryService service = serviceWithAcquiredLock();
        CreateFamilyMemoryRequest request = requestWithMetadata(
                Map.<String, Object>of("source", "DIARY_PROMOTION", "sourceDiaryId", 99));
        when(memoryRepository.findActiveBySourceDiaryId(1L, "99")).thenReturn(null);
        when(memoryMergeService.findSimilar(any(), any(), eq(10L), any(), any())).thenReturn(null);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            service.createFamilyMemory(request);
        }

        verify(memoryRepository).insert(any(MemoryEntry.class));
    }

    @Test
    void createFamilyMemory_shouldValidateRelatedUserMembership() throws InterruptedException {
        MemoryService service = serviceWithAcquiredLock();
        CreateFamilyMemoryRequest request = requestWithMetadata(Map.of());
        request.setRelatedUserId(22L);
        when(memoryMergeService.findSimilar(any(), any(), eq(10L), any(), any())).thenReturn(null);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            service.createFamilyMemory(request);
        }

        verify(familyMembershipFacade).checkMembership(1L, 22L);
    }

    // --- MemoryMergeService ---

    @Test
    void merge_setsSourceAsMergedHeritageAndCallsPersistence() {
        MemoryMergeService mergeService = new MemoryMergeService(memoryRepository, memoryEmbeddingService, memoryVoteService);
        MemoryEntry existing = existingEntry(42L, "ELDER_ADVICE", "FAMILY_VISIBLE",
                "第一段经验内容", new java.util.HashMap<>(Map.of("mergedSourceCount", 0)));

        mergeService.merge(existing, requestWithMetadata(Map.of()),
                Map.<String, Object>of("source", "HERITAGE_INTERVIEW"), 99L);

        ArgumentCaptor<MemoryEntry> captor = ArgumentCaptor.forClass(MemoryEntry.class);
        verify(memoryRepository).updateById(captor.capture());
        verify(memoryEmbeddingService).indexMemoryAfterCommit(captor.getValue());
        Map<?, ?> meta = (Map<?, ?>) captor.getValue().getMetadata();
        assertEquals("MERGED_HERITAGE", meta.get("source"));
        assertTrue(meta.containsKey("mergedAt"));
        assertTrue(meta.containsKey("mergedReason"));
    }

    @Test
    void merge_incrementsMergedSourceCount() {
        MemoryMergeService mergeService = new MemoryMergeService(memoryRepository, memoryEmbeddingService, memoryVoteService);
        MemoryEntry existing = existingEntry(43L, "ELDER_ADVICE", "FAMILY_VISIBLE",
                "原有内容", new java.util.HashMap<>(Map.of("mergedSourceCount", 2)));

        mergeService.merge(existing, requestWithMetadata(Map.of()), Map.<String, Object>of(), 99L);

        ArgumentCaptor<MemoryEntry> captor = ArgumentCaptor.forClass(MemoryEntry.class);
        verify(memoryRepository).updateById(captor.capture());
        assertTrue(((Number) ((Map<?, ?>) captor.getValue().getMetadata()).get("mergedSourceCount")).intValue() >= 3);
    }

    @Test
    void merge_takesHigherImportanceFromIncoming() {
        MemoryMergeService mergeService = new MemoryMergeService(memoryRepository, memoryEmbeddingService, memoryVoteService);
        MemoryEntry existing = existingEntry(44L, "ELDER_ADVICE", "FAMILY_VISIBLE",
                "已有内容", new java.util.HashMap<>());
        existing.setImportance(2);
        CreateFamilyMemoryRequest request = requestWithMetadata(Map.of());
        request.setImportance(5);

        assertEquals(5, mergeService.merge(existing, request, Map.of(), 99L).getImportance());
    }

    @Test
    void merge_keepsHigherImportanceFromExisting() {
        MemoryMergeService mergeService = new MemoryMergeService(memoryRepository, memoryEmbeddingService, memoryVoteService);
        MemoryEntry existing = existingEntry(45L, "ELDER_ADVICE", "FAMILY_VISIBLE",
                "已有内容", new java.util.HashMap<>());
        existing.setImportance(5);
        CreateFamilyMemoryRequest request = requestWithMetadata(Map.of());
        request.setImportance(1);

        assertEquals(5, mergeService.merge(existing, request, Map.of(), 99L).getImportance());
    }

    // --- MemorySearchService ---

    @Test
    void searchFamilyMemories_shouldClampPageAndAttachVoteStats() {
        MemoryVoteService realVoteService = new MemoryVoteService(
                memoryRepository,
                voteRepository,
                familyMembershipFacade);
        MemorySearchService searchService = new MemorySearchService(
                memoryRepository,
                familyMembershipFacade,
                realVoteService);

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
            var result = searchService.searchFamilyMemories(1L, 22L, " 晨读 ", 4, 0);

            assertEquals(2L, result.getPage());
            assertEquals(6L, result.getPageSize());
            assertEquals(9L, result.getTotal());
            assertEquals(1, result.getItems().size());
            assertTrue(((Map<?, ?>) result.getItems().get(0).getMetadata()).containsKey("voteStats"));
        }
    }

    // --- helpers ---

    private MemoryService newService() {
        return new MemoryService(memoryRepository, familyMembershipFacade, memoryEmbeddingService,
                memoryMergeService, memorySearchService, memoryVoteService, redissonClient);
    }

    private MemoryService serviceWithAcquiredLock() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(familyCreateLock);
        when(familyCreateLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(familyCreateLock.isHeldByCurrentThread()).thenReturn(true);
        return newService();
    }

    private static CreateFamilyMemoryRequest requestWithMetadata(Map<String, Object> metadata) {
        CreateFamilyMemoryRequest request = new CreateFamilyMemoryRequest();
        request.setFamilyId(1L);
        request.setContent("爸爸当年选专业只看热门，后来转行代价很大，提醒后辈选择前先看长期适配。");
        request.setType("ELDER_ADVICE");
        request.setScope("FAMILY_VISIBLE");
        request.setSummary("选择前先看长期适配");
        request.setMetadata(WriteMemoryMetadata.fromMap(metadata));
        return request;
    }

    private static MemoryEntry existingEntry(
            Long id, String type, String scope, String content, Map<String, Object> metadata) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setFamilyId(1L);
        entry.setUserId(10L);
        entry.setType(type);
        entry.setScope(scope);
        entry.setContent(content);
        entry.setSummary("摘要");
        entry.setImportance(3);
        entry.setMetadata(metadata);
        return entry;
    }
}
