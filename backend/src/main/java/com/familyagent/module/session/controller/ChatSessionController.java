package com.familyagent.module.session.controller;

import com.familyagent.common.response.Result;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.session.dto.CreateChatSessionRequest;
import com.familyagent.module.session.dto.EndChatSessionRequest;
import com.familyagent.module.session.dto.UpdateSessionMessagesRequest;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    @Operation(summary = "创建家教会话")
    @PostMapping
    public Result<ChatSession> createSession(@Valid @RequestBody CreateChatSessionRequest request) {
        ChatSession session = new ChatSession();
        session.setFamilyId(request.getFamilyId());
        session.setQuestionId(request.getQuestionId());
        session.setSubject(request.getSubject());
        session.setKnowledgePointId(request.getKnowledgePointId());
        session.setMessages(request.getMessages());
        session.setVisibility(request.getVisibility());
        session.setPermissionScope(request.getPermissionScope());
        session.setSource(request.getSource());
        session.setMetadata(request.getMetadata());
        return Result.success(sessionService.createSession(session));
    }

    @Operation(summary = "获取会话详情")
    @GetMapping("/{id}")
    public Result<ChatSession> getSession(@PathVariable Long id) {
        ChatSession session = sessionService.getSession(id);
        CurrentUserGuard.requireSelf(session.getUserId());
        return Result.success(session);
    }

    @Operation(summary = "更新会话消息")
    @PutMapping("/{id}/messages")
    public Result<ChatSession> updateMessages(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSessionMessagesRequest request) {
        return Result.success(sessionService.updateMessages(id, request.getMessages()));
    }

    @Operation(summary = "结束会话")
    @PostMapping("/{id}/end")
    public Result<ChatSession> endSession(
            @PathVariable Long id,
            @RequestBody(required = false) EndChatSessionRequest request) {
        String summary = request == null ? null : request.getSummary();
        return Result.success(sessionService.endSession(id, summary));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return Result.success();
    }


    @Operation(summary = "获取用户会话列表")
    @GetMapping("/user/me")
    public Result<List<ChatSession>> getMySessions(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(sessionService.getUserSessions(CurrentUserGuard.currentUserId(), limit));
    }

    @Operation(summary = "获取用户会话列表")
    @GetMapping("/user/{userId}")
    public Result<List<ChatSession>> getUserSessions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "20") int limit) {
        CurrentUserGuard.requireSelf(userId);
        return Result.success(sessionService.getUserSessions(userId, limit));
    }

    @Operation(summary = "获取活跃会话")
    @GetMapping("/active/me")
    public Result<List<ChatSession>> getMyActiveSessions() {
        return Result.success(sessionService.getActiveSessions(CurrentUserGuard.currentUserId()));
    }

    @Operation(summary = "获取活跃会话")
    @GetMapping("/active/{userId}")
    public Result<List<ChatSession>> getActiveSessions(@PathVariable Long userId) {
        CurrentUserGuard.requireSelf(userId);
        return Result.success(sessionService.getActiveSessions(userId));
    }
}
