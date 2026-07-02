package com.familyagent.module.agent.controller;

import com.familyagent.common.response.Result;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.agent.harness.AgentToolConfirmationDecisionService;
import com.familyagent.module.agent.harness.dto.AgentToolConfirmationDecisionRequest;
import com.familyagent.module.agent.harness.dto.AgentToolConfirmationDecisionResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/tool-confirmations")
@RequiredArgsConstructor
public class AgentToolConfirmationController {

    private final AgentToolConfirmationDecisionService confirmationDecisionService;

    @Operation(summary = "Approve or reject an Agent tool confirmation")
    @PostMapping("/{confirmationId}/decision")
    public Result<AgentToolConfirmationDecisionResult> decide(
            @PathVariable Long confirmationId,
            @Valid @RequestBody AgentToolConfirmationDecisionRequest request) {
        return Result.success(confirmationDecisionService.decide(
                confirmationId,
                CurrentUserGuard.currentUserId(),
                request.getDecision()));
    }
}
