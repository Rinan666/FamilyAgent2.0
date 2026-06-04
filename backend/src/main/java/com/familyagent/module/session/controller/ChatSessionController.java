package com.familyagent.module.session.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话控制器
 */
@Tag(name = "家教会话")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService sessionService;

    @Operation(summary = "获取会话详情")
    @GetMapping("/{id}")
    public Result<ChatSession> getSession(@PathVariable Long id) {
        return Result.success(sessionService.getSession(id));
    }

    @Operation(summary = "获取用户会话列表")
    @GetMapping("/user/{userId}")
    public Result<List<ChatSession>> getUserSessions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(sessionService.getUserSessions(userId, limit));
    }

    @Operation(summary = "获取活跃会话")
    @GetMapping("/active/{userId}")
    public Result<List<ChatSession>> getActiveSessions(@PathVariable Long userId) {
        return Result.success(sessionService.getActiveSessions(userId));
    }
}
