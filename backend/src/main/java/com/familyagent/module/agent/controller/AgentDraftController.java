package com.familyagent.module.agent.controller;

import com.familyagent.common.response.Result;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.agent.dto.AgentDraftResult;
import com.familyagent.module.agent.dto.AgentOrganizeDraftRequest;
import com.familyagent.module.agent.dto.AgentOrganizedDraft;
import com.familyagent.module.agent.dto.AgentPersonaMaterialDraft;
import com.familyagent.module.agent.dto.AgentPersonaMaterialDraftRequest;
import com.familyagent.module.agent.service.AgentDraftService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentDraftController {

    private final AgentDraftService draftService;

    @Operation(summary = "Generate an editable family draft and record its Agent run")
    @PostMapping("/organize-draft")
    public Result<AgentDraftResult<AgentOrganizedDraft>> organize(
            @Valid @RequestBody AgentOrganizeDraftRequest request) {
        return Result.success(draftService.organize(request, CurrentUserGuard.currentUserId()));
    }

    @Operation(summary = "Generate editable persona material and record its Agent run")
    @PostMapping("/persona-material-draft")
    public Result<AgentDraftResult<AgentPersonaMaterialDraft>> organizePersonaMaterial(
            @Valid @RequestBody AgentPersonaMaterialDraftRequest request) {
        return Result.success(draftService.organizePersonaMaterial(
                request,
                CurrentUserGuard.currentUserId()));
    }
}
