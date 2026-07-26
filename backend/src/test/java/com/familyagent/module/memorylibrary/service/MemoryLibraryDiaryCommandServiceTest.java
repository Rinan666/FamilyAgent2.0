package com.familyagent.module.memorylibrary.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.MemoryLibraryDiaryFacade;
import com.familyagent.module.memory.facade.MemoryLibraryIndexMetadataFacade;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryUpdateRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryLibraryDiaryCommandServiceTest {

    @Test
    void update_shouldPreserveDiaryContractAndScheduleIndexing() {
        MemoryLibraryDiaryFacade diaryFacade = mock(MemoryLibraryDiaryFacade.class);
        DiaryEntry entry = new DiaryEntry();
        entry.setId(44L);
        entry.setFamilyId(10L);
        entry.setUserId(101L);
        entry.setVisibility("FAMILY_VISIBLE");
        when(diaryFacade.findById(44L)).thenReturn(entry);
        MemoryLibraryUpdateRequest request = new MemoryLibraryUpdateRequest();
        request.setFamilyId(10L);
        request.setBody("Updated diary");
        request.setTitle("A day");
        request.setType("DAILY");
        request.setVisibility("PRIVATE");
        request.setTags(List.of("family"));
        MemoryLibraryDiaryCommandService service = new MemoryLibraryDiaryCommandService(
                diaryFacade,
                new MemoryLibraryIndexMetadataFacade());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            service.update(request, 44L);
        }

        assertEquals("Updated diary", entry.getRawText());
        assertEquals("PRIVATE", entry.getVisibility());
        assertEquals("PRIVATE", entry.getPrivacyLevel());
        assertEquals("A day", ((Map<?, ?>) entry.getStructured()).get("title"));
        verify(diaryFacade).update(entry);
    }

    @Test
    void deleteArchived_shouldDeleteDiaryThroughUnifiedSyncFacade() {
        MemoryLibraryDiaryFacade diaryFacade = mock(MemoryLibraryDiaryFacade.class);
        DiaryEntry entry = new DiaryEntry();
        entry.setId(44L);
        entry.setFamilyId(10L);
        entry.setUserId(101L);
        entry.setMetadata(Map.of("status", "ARCHIVED"));
        when(diaryFacade.findById(44L)).thenReturn(entry);
        MemoryLibraryDiaryCommandService service = new MemoryLibraryDiaryCommandService(
                diaryFacade,
                new MemoryLibraryIndexMetadataFacade());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            service.deleteArchived(10L, 44L);
        }

        verify(diaryFacade).delete(44L);
    }
}
