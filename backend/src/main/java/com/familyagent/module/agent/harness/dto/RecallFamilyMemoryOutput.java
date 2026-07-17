package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.memory.facade.AgentMemoryContextMetadata;

public record RecallFamilyMemoryOutput(String context, AgentMemoryContextMetadata metadata) {

    public RecallFamilyMemoryOutput(String context) {
        this(context, AgentMemoryContextMetadata.empty());
    }

    public RecallFamilyMemoryOutput {
        context = context == null ? "" : context;
        metadata = metadata == null ? AgentMemoryContextMetadata.empty() : metadata;
    }
}
