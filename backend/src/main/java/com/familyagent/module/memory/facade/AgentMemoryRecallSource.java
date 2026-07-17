package com.familyagent.module.memory.facade;

import java.util.List;

public record AgentMemoryRecallSource(
        String id,
        String sourceType,
        String title,
        String snippet,
        String visibility,
        String temporalLayer,
        List<String> topics,
        List<String> scenes) {

    public AgentMemoryRecallSource {
        topics = topics == null ? List.of() : List.copyOf(topics);
        scenes = scenes == null ? List.of() : List.copyOf(scenes);
    }
}
