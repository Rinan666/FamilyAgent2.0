package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryRecallGrowthFacadeTest {

    @Mock private GrowthGuardRecordRepository growthRecordRepository;

    @Test
    void shouldDelegateAuthorizedRecallQuery() {
        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setId(55L);
        when(growthRecordRepository.findVisibleByFamily(11L, 34L, 15))
                .thenReturn(List.of(record));
        MemoryRecallGrowthFacade facade = new MemoryRecallGrowthFacade(growthRecordRepository);

        assertEquals(List.of(record), facade.findVisibleByFamily(11L, 34L, 15));
        verify(growthRecordRepository).findVisibleByFamily(11L, 34L, 15);
    }
}
