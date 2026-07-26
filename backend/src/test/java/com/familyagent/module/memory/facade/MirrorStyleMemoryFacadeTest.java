package com.familyagent.module.memory.facade;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryLibraryKind;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallCompatibilityProjector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MirrorStyleMemoryFacadeTest {

    @Test
    void loadReadsAllStyleSourcesFromUnifiedMemoryEntries() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        MemoryEntry diary = entry(101L, 11L, MemoryOriginType.DIARY.name());
        MemoryEntry memory = entry(102L, null, null);
        MemoryEntry growth = entry(103L, 13L, MemoryOriginType.GROWTH.name());
        when(repository.findActiveDiaryEntriesByAuthorForStyle(10L, 201L, 80))
                .thenReturn(List.of(diary));
        when(repository.findActiveCanonicalEntriesByAuthorForStyle(10L, 201L, 80))
                .thenReturn(List.of(memory));
        when(repository.findActiveGrowthEntriesBySubjectForStyle(10L, 201L, 80))
                .thenReturn(List.of(growth));
        MirrorStyleMemoryFacade facade = new MirrorStyleMemoryFacade(
                repository,
                new AuthorizedMemoryRecallCompatibilityProjector());

        MirrorStyleMemoryFacade.MirrorStyleRecords records = facade.load(10L, 201L, 80);

        assertEquals(List.of(11L), records.diaries().stream().map(item -> item.getId()).toList());
        assertEquals(List.of(102L), records.memories().stream().map(MemoryEntry::getId).toList());
        assertEquals(List.of(13L), records.growthRecords().stream().map(item -> item.getId()).toList());
        verify(repository).findActiveDiaryEntriesByAuthorForStyle(10L, 201L, 80);
        verify(repository).findActiveCanonicalEntriesByAuthorForStyle(10L, 201L, 80);
        verify(repository).findActiveGrowthEntriesBySubjectForStyle(10L, 201L, 80);
    }

    private static MemoryEntry entry(Long id, Long originId, String originType) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setOriginId(originId);
        entry.setOriginType(originType);
        entry.setLibraryKind(MemoryLibraryKind.FAMILY.name());
        entry.setFamilyId(10L);
        entry.setUserId(201L);
        entry.setRelatedUserId(201L);
        entry.setTitle("Style sample");
        entry.setContent("Content");
        entry.setImportance(3);
        entry.setStatus(EntityStatus.ACTIVE.name());
        return entry;
    }
}
