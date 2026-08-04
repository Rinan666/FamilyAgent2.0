package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.memory.dto.MemoryRecallPlan;

import java.util.List;

public record RecallFamilyMemoryInput(
        String memberMessage,
        List<String> recentUserMessages,
        MemoryRecallPlan recallPlan
) {

    public RecallFamilyMemoryInput(String memberMessage, List<String> recentUserMessages) {
        this(memberMessage, recentUserMessages, null);
    }
}
