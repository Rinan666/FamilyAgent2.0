package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MemoryRecallGrowthFacade {

    private final GrowthGuardRecordRepository growthRecordRepository;

    public List<GrowthGuardRecord> findVisibleByFamily(
            Long familyId,
            Long viewerUserId,
            int limit) {
        return growthRecordRepository.findVisibleByFamily(familyId, viewerUserId, limit);
    }
}
