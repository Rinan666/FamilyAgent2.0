package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentConfirmationStatus;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import org.springframework.stereotype.Component;

@Component
public class AgentConfirmationPolicy {

    public AgentConfirmationStatus evaluate(AgentRunContext context, AgentToolDescriptor descriptor, Object input) {
        if (descriptor.confirmationRequirement() == AgentToolConfirmationRequirement.REQUIRED) {
            return AgentConfirmationStatus.REQUIRED;
        }
        return AgentConfirmationStatus.NOT_REQUIRED;
    }
}
