package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.MemoryIndexDiaryFacade;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.MemoryIndexGrowthFacade;
import com.familyagent.module.memory.dto.RebuildEmbeddingResponse;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryIndexRebuildServiceTest {

    @Mock private MemoryIndexDiaryFacade diaryIndexFacade;
    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private MemoryIndexGrowthFacade growthIndexFacade;
    @Mock private FamilyMembershipFacade familyMembershipFacade;
    @InjectMocks private MemoryIndexRebuildService rebuildService;

    @Test
    void rebuildFamilyIndexes_shouldEnrichAndPersistAllSourceTypes() {
        DiaryEntry diary = new DiaryEntry();
        diary.setId(1L);
        diary.setRawText("Family diary content");
        diary.setStructured(Map.of("entryType", "REFLECTION"));
        diary.setMetadata(Map.of("existing", true));
        MemoryEntry memory = new MemoryEntry();
        memory.setId(2L);
        memory.setContent("Family memory content");
        memory.setSummary("Memory summary");
        memory.setType("ELDER_ADVICE");
        memory.setImportance(4);
        GrowthGuardRecord growthRecord = new GrowthGuardRecord();
        growthRecord.setId(3L);
        growthRecord.setContent("Growth observation content");
        growthRecord.setCategory("LEARNING");
        growthRecord.setSeverity(3);
        growthRecord.setObservedAt(LocalDate.of(2026, 7, 17));
        when(diaryIndexFacade.findActiveByFamily(10L, 200)).thenReturn(List.of(diary));
        when(memoryRepository.findActiveByFamilyForIndexing(10L, 200)).thenReturn(List.of(memory));
        when(growthIndexFacade.findActiveByFamily(10L, 200)).thenReturn(List.of(growthRecord));

        RebuildEmbeddingResponse response = rebuildService.rebuildFamilyIndexes(10L, 0);

        verify(familyMembershipFacade).checkMembership(10L);
        verify(diaryIndexFacade).update(diary);
        verify(memoryRepository).updateById(memory);
        verify(growthIndexFacade).update(growthRecord);
        assertNotNull(diary.getMetadata());
        assertNotNull(memory.getMetadata());
        assertNotNull(growthRecord.getMetadata());
        assertEquals(1, response.getDiaryCount());
        assertEquals(1, response.getMemoryCount());
        assertEquals(1, response.getGrowthRecordCount());
        assertEquals(3, response.getIndexedCount());
    }
}
