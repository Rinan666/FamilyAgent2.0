package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.constant.AgentDraftErrorCode;

final class AgentDraftGenerationException extends BusinessException {

    private final AgentDraftErrorCode errorCode;

    AgentDraftGenerationException(AgentDraftErrorCode errorCode) {
        super(apiErrorCode(errorCode), message(errorCode));
        this.errorCode = errorCode;
    }

    AgentDraftErrorCode errorCode() {
        return errorCode;
    }

    private static ErrorCode apiErrorCode(AgentDraftErrorCode errorCode) {
        return switch (errorCode) {
            case AI_INPUT_REJECTED -> ErrorCode.BAD_REQUEST;
            case AI_RATE_LIMITED -> ErrorCode.RATE_LIMIT_EXCEEDED;
            case AI_TIMEOUT -> ErrorCode.AI_TIMEOUT;
            default -> ErrorCode.AI_SERVICE_ERROR;
        };
    }

    private static String message(AgentDraftErrorCode errorCode) {
        return switch (errorCode) {
            case AI_INPUT_REJECTED -> "Draft content was rejected by the AI safety boundary";
            case AI_RATE_LIMITED -> "AI requests are too frequent; please retry later";
            case AI_TIMEOUT -> "Draft generation timed out; please shorten the content and retry";
            case AI_INVALID_RESPONSE -> "AI returned an invalid draft; please retry";
            default -> "Draft generation is temporarily unavailable";
        };
    }
}
