package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.RecallParticipantSummary;

import java.util.List;

public record AgentMemoryRecallSource(
        String id,
        String sourceType,
        String title,
        String snippet,
        String visibility,
        String temporalLayer,
        List<String> topics,
        List<String> scenes,
        RecallParticipantSummary author,
        RecallParticipantSummary observer,
        RecallParticipantSummary subject) {

    public AgentMemoryRecallSource {
        topics = topics == null ? List.of() : List.copyOf(topics);
        scenes = scenes == null ? List.of() : List.copyOf(scenes);
    }
}
