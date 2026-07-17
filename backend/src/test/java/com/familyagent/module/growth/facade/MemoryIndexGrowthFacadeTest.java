package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryIndexGrowthFacadeTest {

    @Test
    void shouldDelegateIndexQueriesAndUpdates() {
        GrowthGuardRecordRepository repository = mock(GrowthGuardRecordRepository.class);
        GrowthGuardRecord record = new GrowthGuardRecord();
        when(repository.findActiveByFamilyForIndexing(10L, 200)).thenReturn(List.of(record));
        MemoryIndexGrowthFacade facade = new MemoryIndexGrowthFacade(repository);

        assertEquals(List.of(record), facade.findActiveByFamily(10L, 200));
        facade.update(record);

        verify(repository).findActiveByFamilyForIndexing(10L, 200);
        verify(repository).updateById(record);
    }
}
