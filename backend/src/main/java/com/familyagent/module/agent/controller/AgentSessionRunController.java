package com.familyagent.module.agent.controller;

import com.familyagent.common.response.Result;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.agent.dto.AgentRunSummaryView;
import com.familyagent.module.agent.service.AgentRunViewService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/sessions")
@RequiredArgsConstructor
public class AgentSessionRunController {

    private final AgentRunViewService runViewService;

    @Operation(summary = "List user-visible Agent runs for a chat session")
    @GetMapping("/{sessionId}/runs")
    public Result<List<AgentRunSummaryView>> list(@PathVariable Long sessionId) {
        return Result.success(runViewService.listBySession(sessionId, CurrentUserGuard.currentUserId()));
    }
}
