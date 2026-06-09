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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void createFamilyMemory_shouldRejectManualHeritageWithoutPositiveJudge() {
        MemoryService service = new MemoryService(memoryRepository, voteRepository, familyService, memoryEmbeddingService);
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
    void createFamilyMemory_shouldAllowManualHeritageWithPositiveJudge() {
        MemoryService service = new MemoryService(memoryRepository, voteRepository, familyService, memoryEmbeddingService);
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
    void createFamilyMemory_shouldNotRequireJudgeForDiaryPromotion() {
        MemoryService service = new MemoryService(memoryRepository, voteRepository, familyService, memoryEmbeddingService);
        CreateFamilyMemoryRequest request = requestWithMetadata(Map.of("source", "DIARY_PROMOTION", "sourceDiaryId", 99));
        when(memoryRepository.findActiveBySourceDiaryId(1L, "99")).thenReturn(null);
        when(memoryRepository.findActiveFamilyMemories(eq(1L), eq(10L), any(Integer.class))).thenReturn(List.of());

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            service.createFamilyMemory(request);
        }

        verify(memoryRepository).insert(any(MemoryEntry.class));
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
