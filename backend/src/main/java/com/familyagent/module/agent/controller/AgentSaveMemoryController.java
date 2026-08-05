package com.familyagent.module.agent.controller;

import com.familyagent.common.response.Result;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.agent.dto.AgentSaveMemoryRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.service.AgentSaveMemoryCommandService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/memories")
@RequiredArgsConstructor
public class AgentSaveMemoryController {

    private final AgentSaveMemoryCommandService commandService;

    @Operation(summary = "Save an approved Agent memory draft")
    @PostMapping
    public Result<AgentToolCallResult<?>> requestSave(@Valid @RequestBody AgentSaveMemoryRequest request) {
        return Result.success(commandService.requestSave(request, CurrentUserGuard.currentUserId()));
    }
}
