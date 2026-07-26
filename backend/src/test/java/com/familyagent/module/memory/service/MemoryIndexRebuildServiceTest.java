package com.familyagent.module.memory.service;

import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.memory.dto.RebuildEmbeddingResponse;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryIndexRebuildServiceTest {

    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private FamilyMembershipFacade familyMembershipFacade;
    @Mock private UnifiedMemoryIndexMetadataAssembler metadataAssembler;
    @InjectMocks private MemoryIndexRebuildService rebuildService;

    @Test
    void rebuildFamilyIndexesEnrichesAllUnifiedSourceTypes() {
        MemoryEntry diary = entry(101L, "DIARY");
        MemoryEntry memory = entry(2L, null);
        MemoryEntry growth = entry(103L, "GROWTH");
        when(memoryRepository.findActiveFamilyEntriesForIndexing(10L, 200))
                .thenReturn(List.of(diary, memory, growth));
        when(metadataAssembler.enrich(org.mockito.ArgumentMatchers.any())).thenReturn(Map.of("index", Map.of()));

        RebuildEmbeddingResponse response = rebuildService.rebuildFamilyIndexes(10L, 0);

        verify(familyMembershipFacade).checkMembership(10L);
        verify(memoryRepository).updateById(diary);
        verify(memoryRepository).updateById(memory);
        verify(memoryRepository).updateById(growth);
        assertNotNull(diary.getMetadata());
        assertEquals(1, response.getDiaryCount());
        assertEquals(1, response.getMemoryCount());
        assertEquals(1, response.getGrowthRecordCount());
        assertEquals(3, response.getIndexedCount());
    }

    private static MemoryEntry entry(Long id, String originType) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setOriginType(originType);
        entry.setContent("content");
        return entry;
    }
}
