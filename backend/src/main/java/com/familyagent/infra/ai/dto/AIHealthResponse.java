package com.familyagent.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AIHealthResponse(
        String status,
        String service,
        String version,
        String environment,
        @JsonProperty("uptime_seconds") Double uptimeSeconds,
        @JsonProperty("default_model") String defaultModel,
        String errorCode) {

    public static final String STATUS_DOWN = "DOWN";

    public static AIHealthResponse down(String errorCode) {
        return new AIHealthResponse(
                STATUS_DOWN,
                null,
                null,
                null,
                null,
                null,
                errorCode);
    }
}
