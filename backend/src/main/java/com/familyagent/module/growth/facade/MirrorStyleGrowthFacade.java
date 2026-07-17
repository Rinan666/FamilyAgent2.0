package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MirrorStyleGrowthFacade {

    private final GrowthGuardRecordRepository growthRecordRepository;

    public List<GrowthGuardRecord> findActiveByFamilyAndTarget(Long familyId, Long targetUserId, int limit) {
        return growthRecordRepository.findActiveByFamilyAndTargetForStyle(familyId, targetUserId, limit);
    }
}
