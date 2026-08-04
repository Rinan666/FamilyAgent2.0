package com.familyagent.module.session.controller;

import com.familyagent.common.response.Result;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.session.dto.AppendSessionMessagesRequest;
import com.familyagent.module.session.dto.ChatSessionArchiveDetail;
import com.familyagent.module.session.dto.ChatSessionArchiveSummary;
import com.familyagent.module.session.dto.ChatSessionDetail;
import com.familyagent.module.session.dto.ChatSessionMessagePage;
import com.familyagent.module.session.dto.ChatSessionSummary;
import com.familyagent.module.session.dto.CreateChatSessionRequest;
import com.familyagent.module.session.dto.EndChatSessionRequest;
import com.familyagent.module.session.dto.PatchChatSessionRequest;
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
 * Chat session controller.
 */
@Tag(name = "FamilyAgent 会话")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService sessionService;

    @Operation(summary = "创建 FamilyAgent 会话")
    @PostMapping
    public Result<ChatSessionDetail> createSession(@Valid @RequestBody CreateChatSessionRequest request) {
        ChatSession session = new ChatSession();
        session.setFamilyId(request.getFamilyId());
        session.setSubject(request.getSubject());
        session.setTitle(request.getTitle());
        session.setSummary(request.getSummary());
        session.setVisibility(request.getVisibility());
        session.setPermissionScope(request.getPermissionScope());
        session.setSource(request.getSource());
        session.setAgentContextType(request.getAgentContextType());
        session.setTargetUserId(request.getTargetUserId());
        session.setTargetPersonaId(request.getTargetPersonaId());
        session.setMetadata(request.getMetadata());
        return Result.success(sessionService.createSession(session, request.getMessages()));
    }

    @Operation(summary = "获取会话详情")
    @GetMapping("/{id}")
    public Result<ChatSessionDetail> getSession(@PathVariable Long id) {
        return Result.success(sessionService.getSessionDetail(id));
    }

    @Operation(summary = "分页获取会话消息")
    @GetMapping("/{id}/messages")
    public Result<ChatSessionMessagePage> getSessionMessages(
            @PathVariable Long id,
            @RequestParam(required = false) Long beforeSeq,
            @RequestParam(defaultValue = "40") int limit) {
        return Result.success(sessionService.getSessionMessages(id, beforeSeq, limit));
    }

    @Operation(summary = "追加会话消息")
    @PostMapping("/{id}/messages")
    public Result<ChatSessionDetail> appendMessages(
            @PathVariable Long id,
            @Valid @RequestBody AppendSessionMessagesRequest request) {
        return Result.success(sessionService.appendMessages(id, request.getMessages()));
    }

    @Operation(summary = "兼容旧版的尾部消息追加")
    @PutMapping("/{id}/messages")
    public Result<ChatSessionDetail> updateMessages(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSessionMessagesRequest request) {
        return Result.success(sessionService.updateMessages(id, request.getMessages()));
    }

    @Operation(summary = "结束会话")
    @PostMapping("/{id}/end")
    public Result<ChatSessionDetail> endSession(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) EndChatSessionRequest request) {
        String summary = request == null ? null : request.getSummary();
        return Result.success(sessionService.endSession(id, summary, authorization));
    }

    @Operation(summary = "局部更新会话元数据")
    @PatchMapping("/{id}")
    public Result<ChatSessionDetail> patchSession(
            @PathVariable Long id,
            @RequestBody PatchChatSessionRequest request) {
        return Result.success(sessionService.patchMetadata(id, request.getMetadata()));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return Result.success();
    }

    @Operation(summary = "删除当前用户指定家族的 FamilyAgent 会话")
    @DeleteMapping("/family/{familyId}/agent")
    public Result<Integer> deleteFamilyAgentSessions(@PathVariable Long familyId) {
        return Result.success(sessionService.deleteFamilyAgentSessions(familyId));
    }

    @Operation(summary = "获取会话归档摘要")
    @GetMapping("/{id}/archives")
    public Result<List<ChatSessionArchiveSummary>> getArchives(@PathVariable Long id) {
        return Result.success(sessionService.listArchives(id));
    }

    @Operation(summary = "获取会话归档原文")
    @GetMapping("/{id}/archives/{archiveId}")
    public Result<ChatSessionArchiveDetail> getArchiveDetail(
            @PathVariable Long id,
            @PathVariable Long archiveId) {
        return Result.success(sessionService.getArchiveDetail(id, archiveId));
    }

    @Operation(summary = "获取当前用户会话列表")
    @GetMapping("/user/me")
    public Result<List<ChatSessionSummary>> getMySessions(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(sessionService.getUserSessions(CurrentUserGuard.currentUserId(), limit));
    }

    @Operation(summary = "获取指定用户会话列表")
    @GetMapping("/user/{userId}")
    public Result<List<ChatSessionSummary>> getUserSessions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "20") int limit) {
        CurrentUserGuard.requireSelf(userId);
        return Result.success(sessionService.getUserSessions(userId, limit));
    }

    @Operation(summary = "获取当前用户活跃会话")
    @GetMapping("/active/me")
    public Result<List<ChatSessionSummary>> getMyActiveSessions() {
        return Result.success(sessionService.getActiveSessions(CurrentUserGuard.currentUserId()));
    }

    @Operation(summary = "获取指定用户活跃会话")
    @GetMapping("/active/{userId}")
    public Result<List<ChatSessionSummary>> getActiveSessions(@PathVariable Long userId) {
        CurrentUserGuard.requireSelf(userId);
        return Result.success(sessionService.getActiveSessions(userId));
    }
}
