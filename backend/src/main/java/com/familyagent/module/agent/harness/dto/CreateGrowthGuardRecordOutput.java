package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.agent.harness.provenance.AgentCreatedRecordOutput;
import com.familyagent.module.agent.harness.provenance.AgentCreatedRecordType;

public record CreateGrowthGuardRecordOutput(Long growthGuardRecordId) implements AgentCreatedRecordOutput {

    @Override
    public AgentCreatedRecordType recordType() {
        return AgentCreatedRecordType.GROWTH_GUARD_RECORD;
    }

    @Override
    public Long recordId() {
        return growthGuardRecordId;
    }
}
