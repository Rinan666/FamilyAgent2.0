package com.familyagent.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmbeddingResponse {

    private boolean success;
    private boolean degraded;
    private String provider;
    private String model;
    private Integer dimensions;
    private List<Double> embedding;

    @JsonProperty("privacy_categories")
    private List<String> privacyCategories;

    @JsonProperty("errorCode")
    private String errorCode;

    @JsonProperty("latency_ms")
    private Long latencyMs;

    @JsonProperty("request_id")
    private String requestId;

    private String error;

    public static EmbeddingResponse unavailable() {
        EmbeddingResponse response = new EmbeddingResponse();
        response.setSuccess(false);
        response.setDegraded(false);
        response.setErrorCode("AI_SERVICE_UNAVAILABLE");
        response.setError("AI service unavailable");
        return response;
    }
}
