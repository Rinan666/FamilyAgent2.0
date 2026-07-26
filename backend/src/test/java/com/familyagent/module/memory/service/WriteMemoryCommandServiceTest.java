package com.familyagent.module.memory.service;

import com.familyagent.module.diary.dto.CreateDiaryEntryRequest;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.AgentDiaryEntryFacade;
import com.familyagent.module.growth.facade.AgentGrowthGuardRecordFacade;
import com.familyagent.module.memory.dto.WriteMemoryMetadata;
import com.familyagent.module.memory.dto.WriteMemoryRequest;
import com.familyagent.module.memory.dto.WriteMemoryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WriteMemoryCommandServiceTest {

    @Mock private AgentDiaryEntryFacade diaryEntryFacade;
    @Mock private MemoryService memoryService;
    @Mock private PersonalMemoryCommandService personalMemoryCommandService;
    @Mock private AgentGrowthGuardRecordFacade growthGuardRecordFacade;

    private WriteMemoryCommandService service;

    @BeforeEach
    void setUp() {
        service = new WriteMemoryCommandService(
                diaryEntryFacade,
                memoryService,
                personalMemoryCommandService,
                growthGuardRecordFacade);
    }

    @Test
    void writeRecord_shouldUseFacadeAndConvertTypedMetadataAtCompatibilityBoundary() {
        WriteMemoryMetadata metadata = new WriteMemoryMetadata();
        metadata.setSource(" WRITE_MEMORY_SIMPLIFIED ");
        metadata.setAuthorName("Taylor");
        metadata.putExtra("legacyFlag", true);
        WriteMemoryRequest request = new WriteMemoryRequest();
        request.setFamilyId(11L);
        request.setWriteCategory("RECORD");
        request.setContent("A family event");
        request.setTitle("Event title");
        request.setRelatedUserId(34L);
        request.setMetadata(metadata);

        DiaryEntry entry = new DiaryEntry();
        entry.setId(123L);
        entry.setVisibility("FAMILY_VISIBLE");
        when(diaryEntryFacade.create(any())).thenReturn(entry);

        WriteMemoryResult result = service.write(request);

        ArgumentCaptor<CreateDiaryEntryRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateDiaryEntryRequest.class);
        verify(diaryEntryFacade).create(requestCaptor.capture());
        Map<String, Object> persistedMetadata = requestCaptor.getValue().getMetadata().toMap();
        assertEquals("WRITE_MEMORY_SIMPLIFIED", persistedMetadata.get("source"));
        assertEquals("Taylor", persistedMetadata.get("authorName"));
        assertEquals(true, persistedMetadata.get("legacyFlag"));
        assertEquals("RECORD", persistedMetadata.get("writeCategory"));
        assertEquals(true, persistedMetadata.get("disableAutoMerge"));
        assertEquals(34L, persistedMetadata.get("relatedUserId"));
        assertEquals(123L, result.getSavedRecordId());
        assertEquals("DIARY_ENTRY", result.getSavedRecordType());
        verifyNoInteractions(memoryService, personalMemoryCommandService, growthGuardRecordFacade);
    }
}
