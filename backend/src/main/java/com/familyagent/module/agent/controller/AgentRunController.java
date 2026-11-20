package com.familyagent.module.agent.controller;

import com.familyagent.common.response.Result;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.agent.dto.AgentRunView;
import com.familyagent.module.agent.service.AgentRunViewService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/runs")
@RequiredArgsConstructor
public class AgentRunController {

    private final AgentRunViewService runViewService;

    @Operation(summary = "Get a user-visible Agent run trace")
    @GetMapping("/{runId}")
    public Result<AgentRunView> get(@PathVariable Long runId) {
        return Result.success(runViewService.get(runId, CurrentUserGuard.currentUserId()));
    }
}
