package com.familyagent.module.session.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.session.dto.ChatSessionArchiveDetail;
import com.familyagent.module.session.dto.ChatSessionArchiveSummary;
import com.familyagent.module.session.dto.ChatSessionDetail;
import com.familyagent.module.session.dto.ChatSessionMessageItem;
import com.familyagent.module.session.dto.ChatSessionMessagePage;
import com.familyagent.module.session.dto.ChatSessionMessagePayload;
import com.familyagent.module.session.dto.ChatSessionSummary;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.entity.ChatSessionArchive;
import com.familyagent.module.session.entity.ChatSessionMessage;
import com.familyagent.module.session.repository.ChatSessionArchiveRepository;
import com.familyagent.module.session.repository.ChatSessionMessageRepository;
import com.familyagent.module.session.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Chat session service.
 */
@Slf4j
@SuppressWarnings("deprecation")
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final String STATUS_ACTIVE = EntityStatus.ACTIVE.name();
    private static final String STATUS_ENDED = "ENDED";
    private static final int DEFAULT_SESSION_LIMIT = 20;
    private static final int MAX_SESSION_LIMIT = 100;
    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 40;
    private static final int MAX_MESSAGE_PAGE_SIZE = 120;
    private static final int STORAGE_VERSION = 2;
    private static final int MAX_ACTIVE_SESSIONS = 20;

    private final ChatSessionRepository sessionRepository;
    private final ChatSessionMessageRepository messageRepository;
    private final ChatSessionArchiveRepository archiveRepository;
    private final FamilyService familyService;
    private final ChatSessionMessagePersistenceSupport messagePersistenceSupport;
    private final ChatSessionArchiveSupport archiveSupport;
    private final ChatSessionArchiveStorageService archiveStorageService;

    @Transactional
    public ChatSessionDetail createSession(ChatSession session, List<ChatSessionMessagePayload> initialMessages) {
        Long userId = CurrentUserGuard.currentUserId();
        session.setUserId(userId);

        if (sessionRepository.countActiveByUserId(userId) >= MAX_ACTIVE_SESSIONS) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED,
                    "活跃会话数已达上限（" + MAX_ACTIVE_SESSIONS + "），请先结束不再需要的会话");
        }

        if (session.getFamilyId() != null) {
            familyService.checkMembership(session.getFamilyId());
        }
        if (session.getStatus() == null) {
            session.setStatus(STATUS_ACTIVE);
        }
        if (session.getVisibility() == null || session.getVisibility().isBlank()) {
            session.setVisibility(MemoryScope.DEFAULT_SESSION.name());
        }
        if (session.getSource() == null || session.getSource().isBlank()) {
            session.setSource("FAMILY_AGENT");
        }
        session.setMessageCount(0);
        session.setTokenCount(0);
        session.setArchivedBeforeSeq(0);
        session.setArchiveStatus("NONE");
        session.setMetadata(ChatSessionSupportUtils.withStorageVersion(
                ChatSessionSupportUtils.toMutableMap(session.getMetadata()),
                STORAGE_VERSION));
        session.setArchiveMetadata(ChatSessionSupportUtils.toMutableMap(null));
        sessionRepository.insert(session);

        if (initialMessages != null && !initialMessages.isEmpty()) {
            messagePersistenceSupport.appendMessagesInternal(
                    session.getId(),
                    messagePersistenceSupport.normalizePayloads(initialMessages));
        }
        return getSessionDetailOwned(session.getId());
    }

    public ChatSessionDetail getSessionDetail(Long id) {
        return getSessionDetailOwned(id);
    }

    public List<ChatSessionSummary> getUserSessions(Long userId, int limit) {
        CurrentUserGuard.requireSelf(userId);
        return sessionRepository.findByUserId(
                        userId,
                        ChatSessionSupportUtils.normalizeSessionLimit(limit, DEFAULT_SESSION_LIMIT, MAX_SESSION_LIMIT))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    public List<ChatSessionSummary> getActiveSessions(Long userId) {
        CurrentUserGuard.requireSelf(userId);
        return sessionRepository.findActiveByUserId(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    public ChatSessionMessagePage getSessionMessages(Long sessionId, Long beforeSeq, int limit) {
        ChatSession session = getOwnedSessionHeader(sessionId);
        int normalizedLimit = ChatSessionSupportUtils.normalizeMessagePageSize(
                limit,
                DEFAULT_MESSAGE_PAGE_SIZE,
                MAX_MESSAGE_PAGE_SIZE);
        long historyUpperExclusive = archiveSupport.resolveHistoryUpperExclusive(session, beforeSeq);
        if (historyUpperExclusive <= 1 || ChatSessionSupportUtils.safeInt(session.getMessageCount()) == 0) {
            return ChatSessionMessagePage.builder()
                    .items(List.of())
                    .hasMore(false)
                    .nextBeforeSeq(null)
                    .build();
        }

        List<ChatSessionMessage> descending = new ArrayList<>(
                messageRepository.findPageBeforeSeq(sessionId, historyUpperExclusive, normalizedLimit));
        if (descending.size() < normalizedLimit) {
            descending.addAll(archiveSupport.loadArchivedMessagesDescending(
                    sessionId,
                    historyUpperExclusive,
                    normalizedLimit - descending.size()));
        }

        descending.sort((left, right) -> Integer.compare(
                right.getSeq() == null ? 0 : right.getSeq(),
                left.getSeq() == null ? 0 : left.getSeq()));
        if (descending.size() > normalizedLimit) {
            descending = new ArrayList<>(descending.subList(0, normalizedLimit));
        }

        List<ChatSessionMessage> ordered = new ArrayList<>(descending);
        ordered.sort((left, right) -> Integer.compare(
                left.getSeq() == null ? 0 : left.getSeq(),
                right.getSeq() == null ? 0 : right.getSeq()));

        Long nextBeforeSeq = ordered.isEmpty() ? null : ordered.get(0).getSeq().longValue();
        boolean hasMore = nextBeforeSeq != null && nextBeforeSeq > 1;
        return ChatSessionMessagePage.builder()
                .items(ordered.stream().map(this::toMessageItem).toList())
                .hasMore(hasMore)
                .nextBeforeSeq(hasMore ? nextBeforeSeq : null)
                .build();
    }

    @Transactional
    public ChatSessionDetail appendMessages(Long sessionId, List<ChatSessionMessagePayload> messages) {
        getOwnedSessionHeader(sessionId);
        messagePersistenceSupport.appendMessagesInternal(sessionId, messagePersistenceSupport.normalizePayloads(messages));
        archiveSupport.maybeArchiveSession(sessionId);
        return getSessionDetailOwned(sessionId);
    }

    @Transactional
    public ChatSessionDetail updateMessages(Long sessionId, List<ChatSessionMessagePayload> messages) {
        ChatSession session = getOwnedSessionHeader(sessionId);
        List<ChatSessionMessagePayload> normalized = messagePersistenceSupport.normalizePayloads(messages);
        int existingCount = ChatSessionSupportUtils.safeInt(session.getMessageCount());
        if (normalized.size() < existingCount) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Legacy session update cannot truncate persisted history");
        }
        if (normalized.size() == existingCount) {
            return getSessionDetailOwned(sessionId);
        }
        List<ChatSessionMessagePayload> delta = normalized.subList(existingCount, normalized.size());
        messagePersistenceSupport.appendMessagesInternal(sessionId, delta);
        archiveSupport.maybeArchiveSession(sessionId);
        return getSessionDetailOwned(sessionId);
    }

    @Transactional
    public ChatSessionDetail endSession(Long sessionId, String summary, String authorization) {
        getOwnedSessionHeader(sessionId);
        String normalizedSummary = ChatSessionSupportUtils.blankToNull(summary);

        LocalDateTime endedAt = LocalDateTime.now();
        int updated = sessionRepository.endActiveSession(sessionId, normalizedSummary, endedAt);
        ChatSession endedSession = getOwnedSessionHeader(sessionId);

        if (updated == 0) {
            if (STATUS_ENDED.equals(endedSession.getStatus()) && endedSession.getEndedAt() == null) {
                sessionRepository.fillMissingEndedAt(sessionId, endedAt);
                endedSession.setEndedAt(endedAt);
            }
            archiveSupport.maybeArchiveSession(sessionId);
            return getSessionDetailOwned(sessionId);
        }

        endedSession.setStatus(STATUS_ENDED);
        if (normalizedSummary != null) {
            endedSession.setSummary(normalizedSummary);
            sessionRepository.updateById(endedSession);
        }
        endedSession.setEndedAt(endedAt);
        archiveSupport.maybeArchiveSession(sessionId);
        return getSessionDetailOwned(sessionId);
    }

    public ChatSessionDetail endSession(Long sessionId, String summary) {
        return endSession(sessionId, summary, null);
    }

    @Transactional
    public ChatSessionDetail patchMetadata(Long sessionId, Map<String, Object> patch) {
        ChatSession session = getOwnedSessionHeader(sessionId);
        Map<String, Object> current = ChatSessionSupportUtils.toMutableMap(session.getMetadata());
        if (patch != null) {
            current.putAll(patch);
        }
        session.setMetadata(current);
        sessionRepository.updateById(session);
        return getSessionDetailOwned(sessionId);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        ChatSession session = getOwnedSessionHeader(sessionId);
        deleteSessionData(session);
    }

    @Transactional
    public int deleteFamilyAgentSessions(Long familyId) {
        if (familyId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId is required");
        }
        Long userId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(familyId);
        List<ChatSession> sessions = sessionRepository.findFamilyAgentByUserAndFamily(userId, familyId);
        for (ChatSession session : sessions) {
            deleteSessionData(session);
        }
        return sessions.size();
    }

    public List<ChatSessionArchiveSummary> listArchives(Long sessionId) {
        getOwnedSessionHeader(sessionId);
        return archiveRepository.findBySessionId(sessionId).stream()
                .map(this::toArchiveSummary)
                .toList();
    }

    public ChatSessionArchiveDetail getArchiveDetail(Long sessionId, Long archiveId) {
        getOwnedSessionHeader(sessionId);
        ChatSessionArchive archive = archiveRepository.selectById(archiveId);
        if (archive == null || !Objects.equals(archive.getSessionId(), sessionId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        List<ChatSessionMessageItem> transcript = archiveSupport.readArchiveTranscript(archive.getObjectKey()).stream()
                .map(this::toMessageItem)
                .toList();
        return ChatSessionArchiveDetail.builder()
                .id(archive.getId())
                .sessionId(archive.getSessionId())
                .startSeq(archive.getStartSeq())
                .endSeq(archive.getEndSeq())
                .summary(archive.getSummary())
                .objectKey(archive.getObjectKey())
                .messageCount(archive.getMessageCount())
                .tokenCount(archive.getTokenCount())
                .createdAt(archive.getCreatedAt())
                .metadata(ChatSessionSupportUtils.castMap(archive.getMetadata()))
                .transcript(transcript)
                .build();
    }

    @Transactional
    public void backfillLegacySessions() {
        List<ChatSession> legacySessions = sessionRepository.findSessionsNeedingBackfill();
        for (ChatSession session : legacySessions) {
            try {
                backfillSingleSession(session);
            } catch (Exception error) {
                log.warn("Chat session backfill failed: id={}, error={}", session.getId(), error.getMessage());
            }
        }
    }

    private void backfillSingleSession(ChatSession session) {
        ChatSession header = sessionRepository.selectById(session.getId());
        if (header == null) {
            return;
        }
        Map<String, Object> metadata = ChatSessionSupportUtils.withStorageVersion(
                ChatSessionSupportUtils.toMutableMap(header.getMetadata()),
                STORAGE_VERSION);
        List<ChatSessionMessagePayload> payloads = messagePersistenceSupport.parseLegacyMessages(header.getMessages());
        if (payloads.isEmpty()) {
            header.setMessageCount(Math.max(ChatSessionSupportUtils.safeInt(header.getMessageCount()), 0));
            header.setTokenCount(Math.max(ChatSessionSupportUtils.safeInt(header.getTokenCount()), 0));
            header.setMetadata(metadata);
            header.setArchiveMetadata(ChatSessionSupportUtils.toMutableMap(header.getArchiveMetadata()));
            header.setLastMessageAt(header.getLastMessageAt() == null ? header.getStartedAt() : header.getLastMessageAt());
            sessionRepository.updateById(header);
            return;
        }

        Integer maxSeq = messageRepository.findMaxSeqBySessionId(header.getId());
        if (maxSeq != null && maxSeq > 0) {
            header.setMetadata(metadata);
            header.setTitle(ChatSessionSupportUtils.deriveTitle(header, messageRepository.findBySessionId(header.getId())));
            header.setLastMessageAt(ChatSessionSupportUtils.resolveLastMessageAt(messageRepository.findBySessionId(header.getId()), header));
            header.setMessageCount(Math.max(ChatSessionSupportUtils.safeInt(header.getMessageCount()), maxSeq));
            sessionRepository.updateById(header);
            return;
        }

        messagePersistenceSupport.appendMessagesInternal(header.getId(), payloads);
        ChatSession refreshed = sessionRepository.selectById(header.getId());
        refreshed.setMetadata(metadata);
        sessionRepository.updateById(refreshed);
        archiveSupport.maybeArchiveSession(header.getId());
    }

    private ChatSessionDetail getSessionDetailOwned(Long sessionId) {
        ChatSession session = getOwnedSessionHeader(sessionId);
        return ChatSessionDetail.builder()
                .id(session.getId())
                .userId(session.getUserId())
                .familyId(session.getFamilyId())
                .subject(session.getSubject())
                .title(session.getTitle())
                .summary(session.getSummary())
                .status(session.getStatus())
                .visibility(session.getVisibility())
                .source(session.getSource())
                .messageCount(session.getMessageCount())
                .tokenCount(session.getTokenCount())
                .archivedBeforeSeq(session.getArchivedBeforeSeq())
                .archiveStatus(session.getArchiveStatus())
                .lastMessageAt(session.getLastMessageAt())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .metadata(ChatSessionSupportUtils.castMap(session.getMetadata()))
                .archiveMetadata(ChatSessionSupportUtils.toArchiveMetadata(session.getArchiveMetadata()))
                .archives(listArchives(sessionId))
                .build();
    }

    private ChatSession getOwnedSessionHeader(Long id) {
        ChatSession session = sessionRepository.findHeaderById(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        CurrentUserGuard.requireSelf(session.getUserId());
        return session;
    }

    void deleteSessionData(ChatSession session) {
        archiveRepository.findBySessionId(session.getId()).stream()
                .map(ChatSessionArchive::getObjectKey)
                .filter(objectKey -> objectKey != null && !objectKey.isBlank())
                .forEach(archiveStorageService::deleteTranscript);
        archiveRepository.deleteBySessionId(session.getId());
        messageRepository.deleteBySessionId(session.getId());
        sessionRepository.deleteOwnedById(session.getId());
    }

    private ChatSessionSummary toSummary(ChatSession session) {
        return ChatSessionSummary.builder()
                .id(session.getId())
                .userId(session.getUserId())
                .familyId(session.getFamilyId())
                .subject(session.getSubject())
                .title(session.getTitle())
                .summary(session.getSummary())
                .status(session.getStatus())
                .visibility(session.getVisibility())
                .source(session.getSource())
                .messageCount(session.getMessageCount())
                .tokenCount(session.getTokenCount())
                .lastMessageAt(session.getLastMessageAt())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .metadata(ChatSessionSupportUtils.castMap(session.getMetadata()))
                .build();
    }

    private ChatSessionArchiveSummary toArchiveSummary(ChatSessionArchive archive) {
        return ChatSessionArchiveSummary.builder()
                .id(archive.getId())
                .sessionId(archive.getSessionId())
                .startSeq(archive.getStartSeq())
                .endSeq(archive.getEndSeq())
                .summary(archive.getSummary())
                .objectKey(archive.getObjectKey())
                .messageCount(archive.getMessageCount())
                .tokenCount(archive.getTokenCount())
                .createdAt(archive.getCreatedAt())
                .metadata(ChatSessionSupportUtils.castMap(archive.getMetadata()))
                .build();
    }

    private ChatSessionMessageItem toMessageItem(ChatSessionMessage message) {
        return ChatSessionMessageItem.builder()
                .seq(message.getSeq() == null ? null : message.getSeq().longValue())
                .id(message.getClientMessageId())
                .role(message.getRole())
                .content(message.getContent())
                .toolName(message.getToolName())
                .metadata(ChatSessionSupportUtils.castMap(message.getMetadata()))
                .createdAt(message.getCreatedAt())
                .tokenCount(message.getTokenCount())
                .build();
    }
}
