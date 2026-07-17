package com.familyagent.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AIStreamErrorEvent(
        String type,
        boolean error,
        String code,
        String message,
        boolean retryable,
        boolean degraded,
        String requestId,
        Long runId) {

    public static final String ERROR_CODE = "AI_STREAM_UNAVAILABLE";
    public static final String ERROR_MESSAGE = "AI service unavailable, please retry later.";
    private static final String EVENT_TYPE = "error";

    public static AIStreamErrorEvent unavailable(String requestId, Long runId) {
        return new AIStreamErrorEvent(
                EVENT_TYPE,
                true,
                ERROR_CODE,
                ERROR_MESSAGE,
                true,
                false,
                requestId,
                runId);
    }
}
