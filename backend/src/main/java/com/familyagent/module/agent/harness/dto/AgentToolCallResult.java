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
        boolean retryable
) {

    public static <O> AgentToolCallResult<O> success(O data) {
        return new AgentToolCallResult<>(true, data, AgentToolCallStatus.SUCCEEDED, null, null, false);
    }

    public static <O> AgentToolCallResult<O> failure(
            AgentToolCallStatus status,
            String errorCode,
            String message,
            boolean retryable) {
        return new AgentToolCallResult<>(false, null, status, errorCode, message, retryable);
    }
}
