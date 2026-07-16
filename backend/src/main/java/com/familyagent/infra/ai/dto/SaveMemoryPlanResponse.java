package com.familyagent.infra.ai.dto;

import com.familyagent.module.agent.dto.AgentSaveToolPlan;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaveMemoryPlanResponse {

    private boolean success;
    private AgentSaveToolPlan data;

    @JsonAlias({"errorCode", "error_code"})
    private String errorCode;

    private String error;

    public static SaveMemoryPlanResponse unavailable() {
        SaveMemoryPlanResponse response = new SaveMemoryPlanResponse();
        response.setSuccess(false);
        response.setErrorCode("AI_SERVICE_UNAVAILABLE");
        response.setError("AI service unavailable");
        return response;
    }
}
