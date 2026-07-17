package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MirrorStyleMemoryFacadeTest {

    @Test
    void shouldDelegatePrivateStyleQuery() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        MemoryEntry entry = new MemoryEntry();
        when(repository.findActiveByFamilyAndUserForStyle(10L, 201L, 80))
                .thenReturn(List.of(entry));
        MirrorStyleMemoryFacade facade = new MirrorStyleMemoryFacade(repository);

        assertEquals(List.of(entry), facade.findActiveByFamilyAndUser(10L, 201L, 80));
        verify(repository).findActiveByFamilyAndUserForStyle(10L, 201L, 80);
    }
}
