package com.familyagent.module.memory.service;

import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.dto.PersonalMemoryView;
import com.familyagent.module.memory.dto.WriteMemoryMetadata;
import com.familyagent.module.memory.dto.WriteMemoryRequest;
import com.familyagent.module.memory.dto.WriteMemoryResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WriteMemoryCommandServiceTest {

    @Mock private MemoryService memoryService;
    @Mock private PersonalMemoryCommandService personalMemoryCommandService;

    private WriteMemoryCommandService service;

    @BeforeEach
    void setUp() {
        service = new WriteMemoryCommandService(memoryService, personalMemoryCommandService);
    }

    @Test
    void writeRecord_createsCanonicalFamilyMemoryInsteadOfDiaryRoot() {
        WriteMemoryRequest request = request("RECORD");
        request.setDiaryEntryType("SELF_REFLECTION");
        request.setRelatedUserId(34L);
        request.setTags(List.of("family", "reflection"));
        MemoryEntry saved = savedFamilyMemory(123L, "INSIGHT");
        when(memoryService.createFamilyMemory(any())).thenReturn(saved);

        WriteMemoryResult result = service.write(request);

        ArgumentCaptor<CreateFamilyMemoryRequest> captor = ArgumentCaptor.forClass(CreateFamilyMemoryRequest.class);
        verify(memoryService).createFamilyMemory(captor.capture());
        assertEquals("INSIGHT", captor.getValue().getType());
        assertEquals(34L, captor.getValue().getRelatedUserId());
        assertEquals(List.of("family", "reflection"), captor.getValue().getTags());
        assertEquals("RECORD", captor.getValue().getMetadata().toMap().get("writeCategory"));
        assertEquals("FAMILY_MEMORY", result.getSavedRecordType());
        assertEquals(123L, result.getSavedRecordId());
        verifyNoInteractions(personalMemoryCommandService);
    }

    @Test
    void writeObservation_usesSimpleObservationTypeWithoutGrowthFields() {
        WriteMemoryRequest request = request("OBSERVATION");
        request.setRelatedUserId(22L);
        request.setMemoryType("PLAN");
        request.setGrowthCategory("VISION");
        request.setGrowthSeverity(5);
        when(memoryService.createFamilyMemory(any())).thenReturn(savedFamilyMemory(124L, "OBSERVATION"));

        service.write(request);

        ArgumentCaptor<CreateFamilyMemoryRequest> captor = ArgumentCaptor.forClass(CreateFamilyMemoryRequest.class);
        verify(memoryService).createFamilyMemory(captor.capture());
        assertEquals("OBSERVATION", captor.getValue().getType());
        assertEquals(22L, captor.getValue().getRelatedUserId());
        assertEquals(4, captor.getValue().getImportance());
    }

    @Test
    void writePersonalMemory_keepsPersonalOwnershipPath() {
        WriteMemoryRequest request = request("EXPERIENCE");
        request.setMemoryLibrary("PERSONAL");
        request.setPersonalMemoryType("KNOWLEDGE");
        PersonalMemoryView saved = new PersonalMemoryView(
                77L,
                10L,
                "PERSONAL",
                "KNOWLEDGE",
                "PRIVATE",
                request.getContent(),
                "Title",
                3,
                BigDecimal.valueOf(0.85),
                "ACTIVE",
                Map.of(),
                List.of(),
                null,
                null);
        when(personalMemoryCommandService.create(any())).thenReturn(saved);

        WriteMemoryResult result = service.write(request);

        assertEquals("PERSONAL_MEMORY", result.getSavedRecordType());
        verify(personalMemoryCommandService).create(any());
        verifyNoInteractions(memoryService);
    }

    private static WriteMemoryRequest request(String category) {
        WriteMemoryRequest request = new WriteMemoryRequest();
        request.setFamilyId(11L);
        request.setWriteCategory(category);
        request.setContent("A family memory");
        request.setTitle("Title");
        request.setVisibility("FAMILY_VISIBLE");
        request.setMetadata(WriteMemoryMetadata.fromMap(Map.of("source", "WRITE_MEMORY_SIMPLIFIED")));
        return request;
    }

    private static MemoryEntry savedFamilyMemory(Long id, String type) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setType(type);
        entry.setScope("FAMILY_VISIBLE");
        entry.setContent("A family memory");
        entry.setTitle("Title");
        entry.setSummary("Title");
        return entry;
    }
}
