package com.familyagent.module.agent.harness.dto;

import java.util.List;

public record RecallFamilyMemoryInput(
        String memberMessage,
        List<String> recentUserMessages
) {
}
