package com.familyagent.module.session.dto;

import com.familyagent.common.constant.AgentContextType;

public record AgentSessionContext(
        AgentContextType contextType,
        Long targetUserId,
        Long targetPersonaId) {

    public AgentSessionContext {
        contextType = contextType == null ? AgentContextType.FAMILY : contextType;
        if (contextType == AgentContextType.FAMILY) {
            targetUserId = null;
            targetPersonaId = null;
        } else if (contextType == AgentContextType.MIRROR) {
            targetPersonaId = null;
        } else {
            targetUserId = null;
        }
    }

    public static AgentSessionContext family() {
        return new AgentSessionContext(AgentContextType.FAMILY, null, null);
    }
}
