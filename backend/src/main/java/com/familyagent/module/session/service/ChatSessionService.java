package com.familyagent.module.session.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.dto.CreateMemoryEntryRequest;
import com.familyagent.module.memory.service.MemoryService;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会话服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ENDED = "ENDED";

    private final ChatSessionRepository sessionRepository;
    private final FamilyService familyService;
    private final AIServiceClient aiServiceClient;
    private final MemoryService memoryService;

    public ChatSession createSession(ChatSession session) {
        session.setUserId(CurrentUserGuard.currentUserId());
        if (session.getFamilyId() != null) {
            familyService.checkMembership(session.getFamilyId());
        }
        if (session.getMessages() == null) {
            session.setMessages(List.of());
        }
        if (session.getStatus() == null) {
            session.setStatus(STATUS_ACTIVE);
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

    public ChatSession endSession(Long sessionId, String summary, String authorization) {
        ChatSession session = getSession(sessionId);
        CurrentUserGuard.requireSelf(session.getUserId());

        LocalDateTime endedAt = LocalDateTime.now();
        int updated = sessionRepository.endActiveSession(sessionId, blankToNull(summary), endedAt);
        ChatSession endedSession = getSession(sessionId);

        if (updated == 0) {
            if (STATUS_ENDED.equals(endedSession.getStatus()) && endedSession.getEndedAt() == null) {
                sessionRepository.fillMissingEndedAt(sessionId, endedAt);
                endedSession.setEndedAt(endedAt);
            }
            log.debug("Session end is idempotent: id={}, status={}", sessionId, endedSession.getStatus());
            return endedSession;
        }

        endedSession.setStatus(STATUS_ENDED);
        if (blankToNull(summary) != null) {
            endedSession.setSummary(summary);
        }
        endedSession.setEndedAt(endedAt);
        extractMemoriesBestEffort(endedSession, authorization);
        return endedSession;
    }

    public ChatSession endSession(Long sessionId, String summary) {
        return endSession(sessionId, summary, null);
    }

    public void deleteSession(Long sessionId) {
        ChatSession session = getSession(sessionId);
        CurrentUserGuard.requireSelf(session.getUserId());
        sessionRepository.deleteById(sessionId);
    }

    @SuppressWarnings("unchecked")
    private void extractMemoriesBestEffort(ChatSession session, String authorization) {
        try {
            Map<String, Object> request = Map.of(
                    "session_id", session.getId(),
                    "subject", session.getSubject() == null ? "" : session.getSubject(),
                    "knowledge_point_id", session.getKnowledgePointId() == null ? "" : session.getKnowledgePointId(),
                    "messages", session.getMessages() == null ? List.of() : session.getMessages(),
                    "summary", session.getSummary() == null ? "" : session.getSummary()
            );
            Map<String, Object> response = aiServiceClient.extractMemories(request, authorization);
            Object rawMemories = response == null ? null : response.get("memories");
            if (!(rawMemories instanceof List<?> memories)) {
                return;
            }
            List<MemoryLogItem> saved = new ArrayList<>();
            for (Object item : memories) {
                if (!(item instanceof Map<?, ?> memory)) {
                    continue;
                }
                String content = asString(memory.get("content"));
                if (content == null || content.isBlank()) {
                    continue;
                }
                CreateMemoryEntryRequest entryRequest = new CreateMemoryEntryRequest();
                entryRequest.setFamilyId(session.getFamilyId());
                entryRequest.setSubject(session.getSubject());
                entryRequest.setKnowledgePointId(session.getKnowledgePointId());
                entryRequest.setType(defaultString(asString(memory.get("type")), "LEARNING"));
                entryRequest.setScope("PRIVATE");
                entryRequest.setContent(content);
                entryRequest.setSummary(asString(memory.get("summary")));
                entryRequest.setImportance(asInt(memory.get("importance"), 3));
                entryRequest.setConfidence(BigDecimal.valueOf(asDouble(memory.get("confidence"), 0.7)));
                entryRequest.setSourceSessionId(session.getId());
                entryRequest.setMetadata(Map.of("source", "AI_EXTRACTOR"));
                var savedEntry = memoryService.createMemory(entryRequest);
                saved.add(new MemoryLogItem(savedEntry.getId(), savedEntry.getType()));
            }
            if (!saved.isEmpty()) {
                log.info("Extracted {} learning memories from session {}", saved.size(), session.getId());
            }
        } catch (Exception e) {
            log.warn("Memory extraction skipped for session {}: {}", session.getId(), e.getMessage());
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private record MemoryLogItem(Long id, String type) {
    }
}
