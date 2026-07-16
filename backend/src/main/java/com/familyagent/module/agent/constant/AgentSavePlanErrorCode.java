package com.familyagent.module.agent.constant;

import java.util.Locale;

public enum AgentSavePlanErrorCode {
    AI_INPUT_REJECTED,
    AI_RATE_LIMITED,
    AI_PROVIDER_ERROR,
    AI_INVALID_RESPONSE,
    AI_TIMEOUT,
    AI_SERVICE_UNAVAILABLE,
    AI_SERVICE_ERROR;

    public static AgentSavePlanErrorCode fromExternal(String value) {
        if (value == null || value.isBlank()) {
            return AI_SERVICE_ERROR;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AI_SERVICE_ERROR;
        }
    }
}
