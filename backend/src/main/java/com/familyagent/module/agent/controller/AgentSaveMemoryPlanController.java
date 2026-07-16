package com.familyagent.module.agent.controller;

import com.familyagent.common.response.Result;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.agent.dto.AgentSaveMemoryPlanRequest;
import com.familyagent.module.agent.dto.AgentSaveMemoryPlanResult;
import com.familyagent.module.agent.service.AgentSaveMemoryPlanService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/save-memory-plan")
@RequiredArgsConstructor
public class AgentSaveMemoryPlanController {

    private final AgentSaveMemoryPlanService planService;

    @Operation(summary = "Plan an Agent save-memory action and record its skill run")
    @PostMapping
    public Result<AgentSaveMemoryPlanResult> plan(@Valid @RequestBody AgentSaveMemoryPlanRequest request) {
        return Result.success(planService.plan(request, CurrentUserGuard.currentUserId()));
    }
}
