package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryLibraryGrowthFacade {

    private final GrowthGuardRecordRepository growthRecordRepository;

    public GrowthGuardRecord findById(Long recordId) {
        return growthRecordRepository.selectById(recordId);
    }

    public void update(GrowthGuardRecord record) {
        growthRecordRepository.updateById(record);
    }

    public void delete(Long recordId) {
        growthRecordRepository.deleteById(recordId);
    }
}
