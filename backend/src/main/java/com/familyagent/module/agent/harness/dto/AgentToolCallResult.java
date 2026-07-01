package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;

/**
 * Structured result for Agent tool execution.
 */
public record AgentToolCallResult<O>(
        boolean success,
        O data,
        AgentToolCallStatus status,
        String errorCode,
        String message,
        Long confirmationId,
        boolean retryable
) {

    public static <O> AgentToolCallResult<O> success(O data) {
        return new AgentToolCallResult<>(true, data, AgentToolCallStatus.SUCCEEDED, null, null, null, false);
    }

    public static <O> AgentToolCallResult<O> failure(
            AgentToolCallStatus status,
            String errorCode,
            String message,
            boolean retryable) {
        return new AgentToolCallResult<>(false, null, status, errorCode, message, null, retryable);
    }

    public static <O> AgentToolCallResult<O> confirmationRequired(
            String errorCode,
            String message,
            Long confirmationId) {
        return new AgentToolCallResult<>(
                false,
                null,
                AgentToolCallStatus.CONFIRMATION_REQUIRED,
                errorCode,
                message,
                confirmationId,
                false);
    }
}
