package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryLibraryMemoryFacadeTest {

    @Test
    void shouldDelegateMemoryLibraryPersistenceOperations() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        MemoryEntry entry = new MemoryEntry();
        when(repository.selectById(88L)).thenReturn(entry);
        MemoryLibraryMemoryFacade facade = new MemoryLibraryMemoryFacade(repository);

        assertEquals(entry, facade.findById(88L));
        facade.update(entry);
        facade.delete(88L);

        verify(repository).selectById(88L);
        verify(repository).updateById(entry);
        verify(repository).deleteById(88L);
    }
}
