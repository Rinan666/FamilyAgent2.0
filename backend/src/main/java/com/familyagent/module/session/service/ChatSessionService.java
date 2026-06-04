package com.familyagent.module.session.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
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

    public ChatSession createSession(ChatSession session) {
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

    public void updateMessages(Long sessionId, String messages) {
        ChatSession session = getSession(sessionId);
        session.setMessages(messages);
        sessionRepository.updateById(session);
    }

    public void endSession(Long sessionId, String summary) {
        ChatSession session = getSession(sessionId);
        session.setStatus("ENDED");
        session.setSummary(summary);
        session.setEndedAt(java.time.LocalDateTime.now());
        sessionRepository.updateById(session);
    }
}
