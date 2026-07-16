package com.familyagent.module.agent.service;

import com.familyagent.module.agent.constant.AgentDraftSkillContract;
import com.familyagent.module.agent.harness.constant.AgentRunStepType;
import com.familyagent.module.agent.harness.constant.AgentTracePrivacyCategory;
import com.familyagent.module.agent.harness.dto.AgentTraceSpanDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentDraftTraceFactory {

    public AgentTraceSpanDescriptor create(AgentDraftSkillContract contract) {
        return new AgentTraceSpanDescriptor(
                AgentRunStepType.SKILL,
                contract.getOperation(),
                null,
                null,
                null,
                contract.getPromptVersion(),
                contract.getSkillVersion(),
                List.of(AgentTracePrivacyCategory.FAMILY_DATA));
    }
}
