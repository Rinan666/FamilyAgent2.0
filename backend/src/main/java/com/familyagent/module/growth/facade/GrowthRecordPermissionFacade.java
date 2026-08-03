package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.service.PermissionGate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrowthRecordPermissionFacade {

    private final PermissionGate permissionGate;

    public void checkMembership(Long familyId) {
        permissionGate.checkMembership(familyId);
    }

    public void ensureCanView(GrowthGuardRecord record, Long viewerUserId) {
        permissionGate.ensureCanViewRecord(record, viewerUserId);
    }

    public void ensureCanModify(GrowthGuardRecord record) {
        permissionGate.ensureCanModifyRecord(record);
    }
}
