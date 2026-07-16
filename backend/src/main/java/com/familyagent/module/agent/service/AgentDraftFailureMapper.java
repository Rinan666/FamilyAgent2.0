package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.constant.AgentDraftErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AgentDraftFailureMapper {

    public String errorCode(RuntimeException error) {
        if (error instanceof AgentDraftGenerationException draftError) {
            return draftError.errorCode().name();
        }
        if (error instanceof BusinessException businessError) {
            if (businessError.getCode() == ErrorCode.BAD_REQUEST.getCode()) {
                return AgentDraftErrorCode.AI_INPUT_REJECTED.name();
            }
            if (businessError.getCode() == ErrorCode.RATE_LIMIT_EXCEEDED.getCode()) {
                return AgentDraftErrorCode.AI_RATE_LIMITED.name();
            }
            if (businessError.getCode() == ErrorCode.AI_TIMEOUT.getCode()) {
                return AgentDraftErrorCode.AI_TIMEOUT.name();
            }
        }
        return AgentDraftErrorCode.AI_SERVICE_ERROR.name();
    }

    public BusinessException toBusinessException(RuntimeException error) {
        if (error instanceof BusinessException businessError) {
            return businessError;
        }
        return new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Draft generation is temporarily unavailable");
    }
}
