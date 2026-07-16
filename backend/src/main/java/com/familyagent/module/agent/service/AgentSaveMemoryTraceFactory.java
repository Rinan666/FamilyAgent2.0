package com.familyagent.module.agent.service;

import com.familyagent.module.agent.constant.AgentSaveMemorySkillContract;
import com.familyagent.module.agent.harness.constant.AgentRunStepType;
import com.familyagent.module.agent.harness.constant.AgentTracePrivacyCategory;
import com.familyagent.module.agent.harness.dto.AgentTraceSpanDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentSaveMemoryTraceFactory {

    public AgentTraceSpanDescriptor create() {
        return new AgentTraceSpanDescriptor(
                AgentRunStepType.SKILL,
                AgentSaveMemorySkillContract.OPERATION,
                null,
                null,
                null,
                AgentSaveMemorySkillContract.PROMPT_VERSION,
                AgentSaveMemorySkillContract.SKILL_VERSION,
                List.of(AgentTracePrivacyCategory.FAMILY_DATA));
    }
}
