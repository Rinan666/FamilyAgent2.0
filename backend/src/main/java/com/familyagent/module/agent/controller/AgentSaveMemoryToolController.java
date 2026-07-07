package com.familyagent.module.agent.controller;

import com.familyagent.common.response.Result;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.agent.dto.AgentSaveMemoryToolRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.service.AgentSaveMemoryToolCommandService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/save-memory-tool")
@RequiredArgsConstructor
public class AgentSaveMemoryToolController {

    private final AgentSaveMemoryToolCommandService commandService;

    @Operation(summary = "Request an Agent save-memory tool call")
    @PostMapping
    public Result<AgentToolCallResult<?>> requestSave(@Valid @RequestBody AgentSaveMemoryToolRequest request) {
        return Result.success(commandService.requestSave(request, CurrentUserGuard.currentUserId()));
    }
}
