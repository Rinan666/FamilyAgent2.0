package com.familyagent.module.agent.service;

import com.familyagent.module.memory.facade.AgentMemoryContextMetadata;

public record AgentChatMemoryResolution(String context, AgentMemoryContextMetadata metadata) {

    public AgentChatMemoryResolution {
        context = context == null ? "" : context;
        metadata = metadata == null ? AgentMemoryContextMetadata.empty() : metadata;
    }

    public static AgentChatMemoryResolution contextOnly(String context) {
        return new AgentChatMemoryResolution(context, AgentMemoryContextMetadata.empty());
    }

    public static AgentChatMemoryResolution empty() {
        return contextOnly("");
    }
}
