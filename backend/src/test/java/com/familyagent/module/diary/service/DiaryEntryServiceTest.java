package com.familyagent.module.diary.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.module.diary.dto.CreateDiaryEntryRequest;
import com.familyagent.module.diary.dto.DiaryEntryMetadata;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.facade.MemoryIndexingFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryEntryServiceTest {

    @Mock private DiaryEntryRepository diaryRepository;
    @Mock private FamilyService familyService;
    @Mock private MemoryIndexingFacade memoryEmbeddingService;
    @Mock private DiaryMemorySyncSupport memorySyncSupport;

    @Test
    void create_shouldMergeOnlyManualSelfDiaryWhenThereIsExactlyOneCandidate() {
        DiaryEntry existing = existingDiary(99L, "今天早上去买菜");
        when(diaryRepository.findSameDayMergeCandidates(1L, 10L, "PRIVATE", "2026-06-08"))
                .thenReturn(List.of(existing));
        DiaryEntryService service = service();
        CreateDiaryEntryRequest request = manualRequest(Map.of("eventAt", "2026-06-08T09:30:00Z"));
        request.setContent("下午又补记了一段");

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            DiaryEntry result = service.create(request);

            assertEquals(99L, result.getId());
            assertTrue(result.getRawText().contains("今天早上去买菜"));
            assertTrue(result.getRawText().contains("下午又补记了一段"));
            Map<?, ?> metadata = (Map<?, ?>) result.getMetadata();
            assertTrue(Boolean.TRUE.equals(metadata.get("autoMerged")));
            assertEquals(2, ((Number) metadata.get("mergedCount")).intValue());
            assertEquals("MANUAL_SELF_SINGLE_CANDIDATE", metadata.get("mergePolicy"));
        }

        verify(diaryRepository, never()).insert(any(DiaryEntry.class));
        verify(diaryRepository).updateById(existing);
        verify(memoryEmbeddingService).indexDiaryAfterCommit(existing);
    }

    @Test
    void create_shouldNotMergeWhenThereAreMultipleSameDayCandidates() {
        DiaryEntry existingA = existingDiary(99L, "第一条");
        DiaryEntry existingB = existingDiary(100L, "第二条");
        when(diaryRepository.findSameDayMergeCandidates(1L, 10L, "PRIVATE", "2026-06-08"))
                .thenReturn(List.of(existingA, existingB));

        DiaryEntryService service = service();
        CreateDiaryEntryRequest request = manualRequest(Map.of("eventAt", "2026-06-08T09:30:00Z"));

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            service.create(request);
        }

        verify(diaryRepository).insert(any(DiaryEntry.class));
        verify(diaryRepository, never()).updateById(any(DiaryEntry.class));
    }

    @Test
    void create_shouldNotMergeWhenDiaryTargetsRelatedUser() {
        DiaryEntryService service = service();
        CreateDiaryEntryRequest request = manualRequest(Map.of(
                "eventAt", "2026-06-08T09:30:00Z",
                "relatedUserId", 22,
                "relatedMemberName", "妈妈"));

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            service.create(request);
        }

        verify(diaryRepository, never()).findSameDayMergeCandidates(any(), any(), any(), any());
        verify(diaryRepository).insert(any(DiaryEntry.class));
    }

    @Test
    void create_shouldNotMergeForNonManualSource() {
        DiaryEntryService service = service();
        CreateDiaryEntryRequest request = manualRequest(Map.of(
                "eventAt", "2026-06-08T09:30:00Z",
                "source", "HERITAGE_TASK_COMPLETION"));
        request.setEntryType("IMPORTANT_EVENT");

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            service.create(request);
        }

        verify(diaryRepository, never()).findSameDayMergeCandidates(any(), any(), any(), any());
        verify(diaryRepository).insert(any(DiaryEntry.class));
    }

    @Test
    void create_shouldPersistManualSourceOnNewEntry() {
        DiaryEntryService service = service();
        CreateDiaryEntryRequest request = manualRequest(Map.of());

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            service.create(request);
        }

        ArgumentCaptor<DiaryEntry> captor = ArgumentCaptor.forClass(DiaryEntry.class);
        verify(diaryRepository).insert(captor.capture());
        DiaryEntry saved = captor.getValue();
        assertEquals("DIARY_MANUAL", saved.getSource());
        Map<?, ?> metadata = (Map<?, ?>) saved.getMetadata();
        assertEquals("DIARY_MANUAL", metadata.get("source"));
        assertEquals("MANUAL_SELF_SINGLE_CANDIDATE", metadata.get("mergePolicy"));
        assertFalse(Boolean.TRUE.equals(metadata.get("autoMerged")));
        verify(memorySyncSupport).sync(saved);
    }

    @Test
    void create_shouldUseFirstLineAsTitleWhenTitleIsBlank() {
        DiaryEntryService service = service();
        CreateDiaryEntryRequest request = manualRequest(Map.of("disableAutoMerge", true));
        request.setTitle(null);
        request.setContent("第一行标题\n第二行正文");

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            service.create(request);
        }

        ArgumentCaptor<DiaryEntry> captor = ArgumentCaptor.forClass(DiaryEntry.class);
        verify(diaryRepository).insert(captor.capture());
        DiaryEntry saved = captor.getValue();
        Map<?, ?> structured = (Map<?, ?>) saved.getStructured();
        assertEquals("第一行标题", structured.get("title"));
    }

    @Test
    void searchFamilyEntries_shouldClampPageAndForwardFilters() {
        DiaryEntryService service = service();
        DiaryEntry entry = existingDiary(201L, "晨练后聊了学校里的事");
        when(diaryRepository.countVisibleByFamilySearch(1L, 10L, 22L, "晨练")).thenReturn(7L);
        when(diaryRepository.searchVisibleByFamily(1L, 10L, 22L, "晨练", 6, 6L)).thenReturn(List.of(entry));

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            var result = service.searchFamilyEntries(1L, 22L, " 晨练 ", 3, 0);

            assertEquals(2L, result.getPage());
            assertEquals(6L, result.getPageSize());
            assertEquals(7L, result.getTotal());
            assertEquals(1, result.getItems().size());
            assertEquals(201L, result.getItems().get(0).getId());
        }
    }

    private DiaryEntryService service() {
        return new DiaryEntryService(
                diaryRepository,
                familyService,
                memoryEmbeddingService,
                memorySyncSupport);
    }

    private static CreateDiaryEntryRequest manualRequest(Map<String, Object> metadata) {
        CreateDiaryEntryRequest request = new CreateDiaryEntryRequest();
        request.setFamilyId(1L);
        request.setContent("今天陪孩子散步，聊了学校的事。 ");
        request.setEntryType("DAILY");
        request.setTitle("今天的小事");
        request.setVisibility("PRIVATE");
        request.setMetadata(DiaryEntryMetadata.fromMap(metadata));
        return request;
    }

    private static DiaryEntry existingDiary(Long id, String rawText) {
        DiaryEntry entry = new DiaryEntry();
        entry.setId(id);
        entry.setUserId(10L);
        entry.setFamilyId(1L);
        entry.setRawText(rawText);
        entry.setVisibility("PRIVATE");
        entry.setMood(null);
        entry.setTags(new String[]{"日常"});
        entry.setStructured(Map.of(
                "entryType", "DAILY",
                "title", "今天的小事",
                "summary", rawText));
        entry.setMetadata(Map.of(
                "status", "ACTIVE",
                "source", "DIARY_MANUAL",
                "mergePolicy", "MANUAL_SELF_SINGLE_CANDIDATE",
                "diaryDate", "2026-06-08"));
        return entry;
    }
}
