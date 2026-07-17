package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.agent.harness.provenance.AgentCreatedRecordOutput;
import com.familyagent.module.agent.harness.provenance.AgentCreatedRecordType;

public record CreateDiaryEntryOutput(Long diaryEntryId) implements AgentCreatedRecordOutput {

    @Override
    public AgentCreatedRecordType recordType() {
        return AgentCreatedRecordType.DIARY_ENTRY;
    }

    @Override
    public Long recordId() {
        return diaryEntryId;
    }
}
