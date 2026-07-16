package com.familyagent.module.agent.service;

import com.familyagent.infra.ai.dto.DraftGenerationResponse;
import com.familyagent.module.agent.constant.AgentDraftErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AgentDraftResponseGuard {

    public <T> T requireData(DraftGenerationResponse<T> response) {
        if (response == null) {
            throw new AgentDraftGenerationException(AgentDraftErrorCode.AI_SERVICE_ERROR);
        }
        if (!response.isSuccess()) {
            throw new AgentDraftGenerationException(AgentDraftErrorCode.fromExternal(response.getErrorCode()));
        }
        if (response.getData() == null) {
            throw new AgentDraftGenerationException(AgentDraftErrorCode.AI_INVALID_RESPONSE);
        }
        return response.getData();
    }
}
