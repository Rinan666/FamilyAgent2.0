package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.agent.harness.constant.AgentConfirmationDecision;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgentToolConfirmationDecisionRequest {

    @NotNull
    private AgentConfirmationDecision decision;
}
