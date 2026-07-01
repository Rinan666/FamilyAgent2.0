package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.dto.CreateGrowthGuardRecordRequest;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.service.GrowthGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentGrowthGuardRecordFacade {

    private final GrowthGuardService growthGuardService;

    public GrowthGuardRecord create(CreateGrowthGuardRecordRequest request) {
        return growthGuardService.createRecord(request);
    }
}
