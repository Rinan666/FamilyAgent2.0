package com.familyagent.module.diary.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.memory.facade.UnifiedMemorySyncFacade;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
        entry.setMetadata(Map.of("relatedUserId", 22L));
        entry.setTags(new String[] {"family"});

        support.sync(entry);

        ArgumentCaptor<UnifiedMemorySyncRequest> captor = ArgumentCaptor.forClass(UnifiedMemorySyncRequest.class);
        verify(syncFacade).sync(captor.capture());
        assertEquals("KNOWLEDGE", captor.getValue().type().name());
        assertEquals("DIARY", captor.getValue().originType().name());
        assertEquals(22L, captor.getValue().relatedUserId());
        assertEquals("FAMILY_VISIBLE", captor.getValue().visibility().name());
    }
}
