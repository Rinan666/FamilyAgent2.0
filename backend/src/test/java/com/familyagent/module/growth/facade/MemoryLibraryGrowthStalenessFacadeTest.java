package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.dto.GrowthStalenessStats;
import com.familyagent.module.growth.repository.GrowthGuardStalenessVoteRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryLibraryGrowthStalenessFacadeTest {

    @Test
    void shouldDelegateGrowthStalenessStats() {
        GrowthGuardStalenessVoteRepository repository = mock(GrowthGuardStalenessVoteRepository.class);
        GrowthStalenessStats stats = new GrowthStalenessStats(55L, 2, 0.7, true);
        when(repository.statsByRecordId(55L, 101L)).thenReturn(stats);
        MemoryLibraryGrowthStalenessFacade facade = new MemoryLibraryGrowthStalenessFacade(repository);

        assertEquals(stats, facade.getStats(55L, 101L));

        verify(repository).statsByRecordId(55L, 101L);
    }
}
