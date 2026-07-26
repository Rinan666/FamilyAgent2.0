package com.familyagent.module.diary.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.memory.facade.UnifiedMemoryCreateResult;
import com.familyagent.module.memory.facade.UnifiedMemorySyncFacade;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiaryMemorySyncSupportTest {

    private final UnifiedMemorySyncFacade syncFacade = mock(UnifiedMemorySyncFacade.class);
    private final DiaryMemorySyncSupport support = new DiaryMemorySyncSupport(syncFacade);

    @Test
    void sync_mapsDiaryToSimpleCanonicalMemory() {
        DiaryEntry entry = new DiaryEntry();
        entry.setId(51L);
        entry.setUserId(10L);
        entry.setFamilyId(1L);
        entry.setRawText("A lesson from today");
        entry.setVisibility("LEGACY_VISIBLE");
        entry.setStructured(Map.of("entryType", "LESSON", "title", "Today"));
        entry.setMood("CALM");
        entry.setSource("MANUAL");
        entry.setMetadata(Map.of("relatedUserId", 22L));
        entry.setTags(new String[] {"family"});

        support.sync(entry);

        ArgumentCaptor<UnifiedMemorySyncRequest> captor = ArgumentCaptor.forClass(UnifiedMemorySyncRequest.class);
        verify(syncFacade).sync(captor.capture());
        assertEquals("KNOWLEDGE", captor.getValue().type().name());
        assertEquals("DIARY", captor.getValue().originType().name());
        assertEquals(22L, captor.getValue().relatedUserId());
        assertEquals("FAMILY_VISIBLE", captor.getValue().visibility().name());
        assertEquals("LESSON", captor.getValue().metadata().legacyDiary().entryType());
        assertEquals("CALM", captor.getValue().metadata().legacyDiary().mood());
        assertEquals(22L, captor.getValue().metadata().extra().get("relatedUserId"));
    }

    @Test
    void createUsesServerAllocatedPublicId() {
        DiaryEntry entry = new DiaryEntry();
        entry.setUserId(10L);
        entry.setFamilyId(1L);
        entry.setRawText("Keep this");
        entry.setVisibility("PRIVATE");
        entry.setStructured(Map.of("entryType", "DAILY", "title", "Today"));
        entry.setMetadata(Map.of("status", "ACTIVE"));
        LocalDateTime timestamp = LocalDateTime.of(2026, 7, 27, 12, 0);
        when(syncFacade.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UnifiedMemoryCreateResult(151L, 51L, timestamp, timestamp));

        support.create(entry);

        assertEquals(51L, entry.getId());
        assertEquals(timestamp, entry.getCreatedAt());
        verify(syncFacade).create(org.mockito.ArgumentMatchers.any());
    }
}
