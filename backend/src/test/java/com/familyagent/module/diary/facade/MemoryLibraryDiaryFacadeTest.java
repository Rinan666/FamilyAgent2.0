package com.familyagent.module.diary.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.diary.service.DiaryMemorySyncSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryLibraryDiaryFacadeTest {

    @Test
    void shouldDelegateMemoryLibraryDiaryOperations() {
        DiaryEntryRepository repository = mock(DiaryEntryRepository.class);
        DiaryMemorySyncSupport memorySyncSupport = mock(DiaryMemorySyncSupport.class);
        DiaryEntry entry = new DiaryEntry();
        when(repository.selectById(44L)).thenReturn(entry);
        MemoryLibraryDiaryFacade facade = new MemoryLibraryDiaryFacade(repository, memorySyncSupport);

        assertEquals(entry, facade.findById(44L));
        facade.update(entry);
        facade.delete(44L);

        verify(repository).selectById(44L);
        verify(repository).updateById(entry);
        verify(repository).deleteById(44L);
        verify(memorySyncSupport).sync(entry);
        verify(memorySyncSupport).delete(44L);
    }
}
