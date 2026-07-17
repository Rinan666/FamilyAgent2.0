package com.familyagent.module.diary.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryLibraryDiaryFacadeTest {

    @Test
    void shouldDelegateMemoryLibraryDiaryOperations() {
        DiaryEntryRepository repository = mock(DiaryEntryRepository.class);
        DiaryEntry entry = new DiaryEntry();
        when(repository.selectById(44L)).thenReturn(entry);
        MemoryLibraryDiaryFacade facade = new MemoryLibraryDiaryFacade(repository);

        assertEquals(entry, facade.findById(44L));
        facade.update(entry);
        facade.delete(44L);

        verify(repository).selectById(44L);
        verify(repository).updateById(entry);
        verify(repository).deleteById(44L);
    }
}
