package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MemoryIndexGrowthFacade {

    private final GrowthGuardRecordRepository growthRecordRepository;

    public List<GrowthGuardRecord> findActiveByFamily(Long familyId, int limit) {
        return growthRecordRepository.findActiveByFamilyForIndexing(familyId, limit);
    }

    public void update(GrowthGuardRecord record) {
        growthRecordRepository.updateById(record);
    }
}
