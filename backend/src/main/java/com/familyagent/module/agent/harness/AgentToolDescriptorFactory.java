package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import org.springframework.stereotype.Component;

/**
 * Creates synthetic descriptors for audit-only cases.
 */
@Component
public class AgentToolDescriptorFactory {

    public AgentToolDescriptor unknown(String toolName) {
        return new AgentToolDescriptor(
                toolName,
                "Unknown Agent tool",
                Object.class,
                Object.class,
                AgentToolSideEffect.READ_ONLY,
                AgentToolConfirmationRequirement.NOT_REQUIRED,
                AgentToolPrivacyLevel.INTERNAL_ONLY);
    }
}
