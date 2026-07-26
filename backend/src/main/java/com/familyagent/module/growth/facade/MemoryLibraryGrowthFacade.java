package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.service.GrowthMemorySyncSupport;
import com.familyagent.module.memory.facade.UnifiedGrowthRecordFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryLibraryGrowthFacade {

    private final UnifiedGrowthRecordFacade growthRecords;
    private final GrowthMemorySyncSupport memorySyncSupport;

    public GrowthGuardRecord findById(Long recordId) {
        return growthRecords.findById(recordId);
    }

    public void update(GrowthGuardRecord record) {
        memorySyncSupport.sync(record);
    }

    public void delete(Long recordId) {
        memorySyncSupport.delete(recordId);
    }
}
