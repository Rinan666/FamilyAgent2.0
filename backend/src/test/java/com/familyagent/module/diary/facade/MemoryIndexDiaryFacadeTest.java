package com.familyagent.module.diary.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryIndexDiaryFacadeTest {

    @Test
    void shouldDelegateIndexQueriesAndUpdates() {
        DiaryEntryRepository repository = mock(DiaryEntryRepository.class);
        DiaryEntry entry = new DiaryEntry();
        when(repository.findActiveByFamilyForIndexing(10L, 200)).thenReturn(List.of(entry));
        MemoryIndexDiaryFacade facade = new MemoryIndexDiaryFacade(repository);

        assertEquals(List.of(entry), facade.findActiveByFamily(10L, 200));
        facade.update(entry);

        verify(repository).findActiveByFamilyForIndexing(10L, 200);
        verify(repository).updateById(entry);
    }
}
