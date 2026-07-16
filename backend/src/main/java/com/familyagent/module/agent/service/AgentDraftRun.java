package com.familyagent.module.agent.service;

import com.familyagent.module.agent.constant.AgentDraftSkillContract;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.skillrun.entity.SkillRun;

record AgentDraftRun(
        AgentDraftSkillContract contract,
        AgentRunContext context,
        AgentRunStepRecord span,
        SkillRun skillRun
) {
}
