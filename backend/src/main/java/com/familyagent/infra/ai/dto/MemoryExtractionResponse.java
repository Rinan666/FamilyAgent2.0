package com.familyagent.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryExtractionResponse {

    private boolean success;
    private boolean degraded;
    private boolean deprecated;
    private List<ExtractedMemory> memories;
    private String message;

    @JsonAlias("error_code")
    @JsonProperty("errorCode")
    private String errorCode;

    private String error;

    public static MemoryExtractionResponse unavailable() {
        MemoryExtractionResponse response = new MemoryExtractionResponse();
        response.setSuccess(false);
        response.setDegraded(false);
        response.setMemories(List.of());
        response.setErrorCode("AI_SERVICE_UNAVAILABLE");
        response.setError("AI service unavailable");
        return response;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractedMemory {
        private String type;
        private String content;
        private String summary;
        private Integer importance;
        private Double confidence;
    }
}
