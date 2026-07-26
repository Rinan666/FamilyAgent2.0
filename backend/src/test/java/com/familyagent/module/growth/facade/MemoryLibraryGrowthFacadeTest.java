package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.service.GrowthMemorySyncSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryLibraryGrowthFacadeTest {

    @Test
    void shouldDelegateMemoryLibraryGrowthOperations() {
        GrowthGuardRecordRepository repository = mock(GrowthGuardRecordRepository.class);
        GrowthMemorySyncSupport memorySyncSupport = mock(GrowthMemorySyncSupport.class);
        GrowthGuardRecord record = new GrowthGuardRecord();
        when(repository.selectById(55L)).thenReturn(record);
        MemoryLibraryGrowthFacade facade = new MemoryLibraryGrowthFacade(repository, memorySyncSupport);

        assertEquals(record, facade.findById(55L));
        facade.update(record);
        facade.delete(55L);

        verify(repository).selectById(55L);
        verify(repository).updateById(record);
        verify(repository).deleteById(55L);
        verify(memorySyncSupport).sync(record);
        verify(memorySyncSupport).delete(55L);
    }
}
