package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MirrorStyleGrowthFacadeTest {

    @Test
    void shouldDelegatePrivateStyleQuery() {
        GrowthGuardRecordRepository repository = mock(GrowthGuardRecordRepository.class);
        GrowthGuardRecord record = new GrowthGuardRecord();
        when(repository.findActiveByFamilyAndTargetForStyle(10L, 201L, 80))
                .thenReturn(List.of(record));
        MirrorStyleGrowthFacade facade = new MirrorStyleGrowthFacade(repository);

        assertEquals(List.of(record), facade.findActiveByFamilyAndTarget(10L, 201L, 80));
        verify(repository).findActiveByFamilyAndTargetForStyle(10L, 201L, 80);
    }
}
