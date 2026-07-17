package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.memory.dto.MemoryRecallContextMetadata;

public record RecallFamilyMemoryOutput(String context, MemoryRecallContextMetadata metadata) {

    public RecallFamilyMemoryOutput(String context) {
        this(context, MemoryRecallContextMetadata.empty());
    }

    public RecallFamilyMemoryOutput {
        context = context == null ? "" : context;
        metadata = metadata == null ? MemoryRecallContextMetadata.empty() : metadata;
    }
}
