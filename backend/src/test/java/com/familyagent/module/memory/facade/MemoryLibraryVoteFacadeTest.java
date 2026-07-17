package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.MemoryVoteStats;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryLibraryVoteFacadeTest {

    @Test
    void shouldDelegateMemoryVoteStats() {
        MemoryEntryVoteRepository repository = mock(MemoryEntryVoteRepository.class);
        MemoryVoteStats stats = new MemoryVoteStats(88L, 2, 1, 1, 1.3, "UP");
        when(repository.statsByMemoryId(88L, 101L)).thenReturn(stats);
        MemoryLibraryVoteFacade facade = new MemoryLibraryVoteFacade(repository);

        assertEquals(stats, facade.getStats(88L, 101L));

        verify(repository).statsByMemoryId(88L, 101L);
    }
}
