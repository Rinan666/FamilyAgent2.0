package com.familyagent.infra.ai.dto;

import com.familyagent.module.agent.constant.AgentDraftErrorCode;
import com.familyagent.module.agent.dto.AgentOrganizedDraft;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrganizeDraftResponse implements DraftGenerationResponse<AgentOrganizedDraft> {

    private boolean success;
    private AgentOrganizedDraft data;

    @JsonAlias({"errorCode", "error_code"})
    private String errorCode;

    private String error;

    public static OrganizeDraftResponse failure(AgentDraftErrorCode errorCode) {
        OrganizeDraftResponse response = new OrganizeDraftResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode.name());
        response.setError("Draft organization unavailable");
        return response;
    }
}
