package com.familyagent.module.agent.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentChatMemoryResolution(String context, Map<String, Object> metadata) {

    public AgentChatMemoryResolution {
        context = context == null ? "" : context;
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public static AgentChatMemoryResolution contextOnly(String context) {
        return new AgentChatMemoryResolution(context, Map.of());
    }

    public static AgentChatMemoryResolution empty() {
        return contextOnly("");
    }
}
