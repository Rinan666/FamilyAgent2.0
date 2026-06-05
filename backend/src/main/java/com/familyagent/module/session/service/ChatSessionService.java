package com.familyagent.module.session.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;
    private final FamilyService familyService;

    public ChatSession createSession(ChatSession session) {
        session.setUserId(CurrentUserGuard.currentUserId());
        if (session.getFamilyId() != null) {
            familyService.checkMembership(session.getFamilyId());
        }
        if (session.getMessages() == null) {
            session.setMessages(List.of());
        }
        if (session.getStatus() == null) {
            session.setStatus("ACTIVE");
        }
        if (session.getVisibility() == null) {
            session.setVisibility("PRIVATE");
        }
        if (session.getSource() == null) {
            session.setSource("TUTOR");
        }
        sessionRepository.insert(session);
        log.info("会话创建: id={}, userId={}", session.getId(), session.getUserId());
        return session;
    }

    public ChatSession getSession(Long id) {
        ChatSession session = sessionRepository.selectById(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return session;
    }

    public List<ChatSession> getUserSessions(Long userId, int limit) {
        return sessionRepository.findByUserId(userId, limit);
    }

    public List<ChatSession> getActiveSessions(Long userId) {
        return sessionRepository.findActiveByUserId(userId);
    }

    public ChatSession updateMessages(Long sessionId, Object messages) {
        ChatSession session = getSession(sessionId);
        CurrentUserGuard.requireSelf(session.getUserId());
        session.setMessages(messages);
        sessionRepository.updateById(session);
        return session;
    }

    public ChatSession endSession(Long sessionId, String summary) {
        ChatSession session = getSession(sessionId);
        CurrentUserGuard.requireSelf(session.getUserId());
        session.setStatus("ENDED");
        session.setSummary(summary);
        session.setEndedAt(java.time.LocalDateTime.now());
        sessionRepository.updateById(session);
        return session;
    }

    public void deleteSession(Long sessionId) {
        ChatSession session = getSession(sessionId);
        CurrentUserGuard.requireSelf(session.getUserId());
        sessionRepository.deleteById(sessionId);
    }
}
