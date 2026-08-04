package com.familyagent.module.agent.dto;

import com.familyagent.common.constant.AgentContextScope;
import com.familyagent.common.constant.AgentContextType;

public record AgentIntentPlan(
        AgentContextType contextType,
        AgentContextScope contextScope,
        Long targetUserId,
        Long targetPersonaId,
        String targetLabel,
        String effectiveMessage,
        AgentResponsePlan responsePlan,
        boolean contextChanged,
        String directResponseMessage) {

    public AgentIntentPlan {
        contextType = contextType == null ? AgentContextType.FAMILY : contextType;
        contextScope = contextScope == null ? AgentContextScope.TURN : contextScope;
        effectiveMessage = effectiveMessage == null ? "" : effectiveMessage.trim();
        targetLabel = targetLabel == null || targetLabel.isBlank() ? null : targetLabel.trim();
        responsePlan = responsePlan == null
                ? new AgentResponsePlan(null, null, null, false, false)
                : responsePlan;
        directResponseMessage = directResponseMessage == null || directResponseMessage.isBlank()
                ? null
                : directResponseMessage.trim();
    }

    public boolean hasDirectResponse() {
        return directResponseMessage != null;
    }
}
