package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.agent.harness.constant.AgentRunStepType;
import com.familyagent.module.agent.harness.constant.AgentTracePrivacyCategory;

import java.util.List;

public record AgentTraceSpanDescriptor(
        AgentRunStepType stepType,
        String operation,
        String parentSpanId,
        String provider,
        String model,
        String promptVersion,
        String skillVersion,
        List<AgentTracePrivacyCategory> privacyCategories
) {
    public AgentTraceSpanDescriptor {
        privacyCategories = privacyCategories == null ? List.of() : List.copyOf(privacyCategories);
    }
}
