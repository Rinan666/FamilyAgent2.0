package com.familyagent.module.agent.dto;

import com.familyagent.common.constant.AgentAnswerDepth;
import com.familyagent.common.constant.AgentWebSearchPolicy;
import com.familyagent.common.constant.MemoryRecallDepth;
import com.familyagent.infra.ai.dto.AgentResponsePlanPayload;

public record AgentResponsePlan(
        AgentAnswerDepth answerDepth,
        MemoryRecallDepth recallDepth,
        AgentWebSearchPolicy webSearchPolicy,
        boolean decisionSupport,
        boolean degraded) {

    public AgentResponsePlan {
        answerDepth = answerDepth == null ? AgentAnswerDepth.STANDARD : answerDepth;
        recallDepth = recallDepth == null ? MemoryRecallDepth.STANDARD : recallDepth;
        webSearchPolicy = webSearchPolicy == null ? AgentWebSearchPolicy.AUTO : webSearchPolicy;
    }

    public AgentResponsePlanPayload toAiPayload() {
        return new AgentResponsePlanPayload(
                answerDepth.name(),
                recallDepth.name(),
                webSearchPolicy.name(),
                decisionSupport,
                degraded);
    }
}
