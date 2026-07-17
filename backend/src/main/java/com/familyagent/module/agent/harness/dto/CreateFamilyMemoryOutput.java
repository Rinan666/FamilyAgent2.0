package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.agent.harness.provenance.AgentCreatedRecordOutput;
import com.familyagent.module.agent.harness.provenance.AgentCreatedRecordType;

public record CreateFamilyMemoryOutput(Long memoryEntryId) implements AgentCreatedRecordOutput {

    @Override
    public AgentCreatedRecordType recordType() {
        return AgentCreatedRecordType.MEMORY_ENTRY;
    }

    @Override
    public Long recordId() {
        return memoryEntryId;
    }
}
