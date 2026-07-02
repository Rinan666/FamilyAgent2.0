package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;

/**
 * Stable descriptor for an Agent tool.
 */
public record AgentToolDescriptor(
        String name,
        String description,
        Class<?> inputType,
        Class<?> outputType,
        AgentToolSideEffect sideEffect,
        AgentToolConfirmationRequirement confirmationRequirement,
        AgentToolPrivacyLevel privacyLevel
) {
}
