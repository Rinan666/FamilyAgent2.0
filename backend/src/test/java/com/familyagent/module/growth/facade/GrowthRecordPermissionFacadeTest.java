package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.service.PermissionGate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GrowthRecordPermissionFacadeTest {

    @Mock private PermissionGate permissionGate;
    @InjectMocks private GrowthRecordPermissionFacade facade;

    @Test
    void delegatesRecordPermissionChecks() {
        GrowthGuardRecord record = new GrowthGuardRecord();

        facade.checkMembership(3L);
        facade.ensureCanView(record, 43L);
        facade.ensureCanModify(record);

        verify(permissionGate).checkMembership(3L);
        verify(permissionGate).ensureCanViewRecord(record, 43L);
        verify(permissionGate).ensureCanModifyRecord(record);
    }
}
