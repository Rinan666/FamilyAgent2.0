package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.service.GrowthMemorySyncSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryLibraryGrowthFacade {

    private final GrowthGuardRecordRepository growthRecordRepository;
    private final GrowthMemorySyncSupport memorySyncSupport;

    public GrowthGuardRecord findById(Long recordId) {
        return growthRecordRepository.selectById(recordId);
    }

    public void update(GrowthGuardRecord record) {
        growthRecordRepository.updateById(record);
        memorySyncSupport.sync(record);
    }

    public void delete(Long recordId) {
        growthRecordRepository.deleteById(recordId);
        memorySyncSupport.delete(recordId);
    }
}
