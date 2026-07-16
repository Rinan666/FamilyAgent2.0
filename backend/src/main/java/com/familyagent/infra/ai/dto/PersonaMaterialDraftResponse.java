package com.familyagent.infra.ai.dto;

import com.familyagent.module.agent.constant.AgentDraftErrorCode;
import com.familyagent.module.agent.dto.AgentPersonaMaterialDraft;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PersonaMaterialDraftResponse implements DraftGenerationResponse<AgentPersonaMaterialDraft> {

    private boolean success;
    private AgentPersonaMaterialDraft data;

    @JsonAlias({"errorCode", "error_code"})
    private String errorCode;

    private String error;

    public static PersonaMaterialDraftResponse failure(AgentDraftErrorCode errorCode) {
        PersonaMaterialDraftResponse response = new PersonaMaterialDraftResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode.name());
        response.setError("Persona material organization unavailable");
        return response;
    }
}
