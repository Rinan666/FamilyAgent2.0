package com.familyagent.module.session.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Chat session service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ENDED = "ENDED";
    private static final int DEFAULT_SESSION_LIMIT = 20;
    private static final int MAX_SESSION_LIMIT = 100;
    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 40;
    private static final int MAX_MESSAGE_PAGE_SIZE = 120;
    private static final int ARCHIVE_TRIGGER_MESSAGE_COUNT = 100;
    private static final int ARCHIVE_CHUNK_SIZE = 50;
    private static final int ARCHIVE_RETAIN_RECENT_COUNT = 30;
    private static final int STORAGE_VERSION = 2;

    private final ChatSessionRepository sessionRepository;
    private final ChatSessionMessageRepository messageRepository;
    private final ChatSessionArchiveRepository archiveRepository;
    private final FamilyService familyService;
    private final ObjectMapper objectMapper;
    private final ChatSessionArchiveStorageService archiveStorageService;
    private final ChatSessionArchiveSummaryService archiveSummaryService;

    @Transactional
    public ChatSessionDetail createSession(ChatSession session, List<ChatSessionMessagePayload> initialMessages) {
        session.setUserId(CurrentUserGuard.currentUserId());
        if (session.getFamilyId() != null) {
            familyService.checkMembership(session.getFamilyId());
        }
        if (session.getStatus() == null) {
            session.setStatus(STATUS_ACTIVE);
        }
        if (session.getVisibility() == null || session.getVisibility().isBlank()) {
            session.setVisibility("PRIVATE");
        }
        if (session.getSource() == null || session.getSource().isBlank()) {
            session.setSource("FAMILY_AGENT");
        }
        session.setMessages(null);
        session.setMessageCount(0);
        session.setTokenCount(0);
        session.setArchivedBeforeSeq(0);
        session.setArchiveStatus("NONE");
        session.setMetadata(withStorageVersion(toMutableMap(session.getMetadata())));
        session.setArchiveMetadata(new HashMap<>());
        sessionRepository.insert(session);

        if (initialMessages != null && !initialMessages.isEmpty()) {
            appendMessagesInternal(session.getId(), normalizePayloads(initialMessages));
        }
        return getSessionDetailOwned(session.getId());
    }

    public ChatSessionDetail getSessionDetail(Long id) {
        return getSessionDetailOwned(id);
    }

    public List<ChatSessionSummary> getUserSessions(Long userId, int limit) {
        CurrentUserGuard.requireSelf(userId);
        return sessionRepository.findByUserId(userId, normalizeSessionLimit(limit)).stream()
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
        int normalizedLimit = normalizeMessagePageSize(limit);
        long historyUpperExclusive = resolveHistoryUpperExclusive(session, beforeSeq);
        if (historyUpperExclusive <= 1 || safeInt(session.getMessageCount()) == 0) {
            return ChatSessionMessagePage.builder()
                    .items(List.of())
                    .hasMore(false)
                    .nextBeforeSeq(null)
                    .build();
        }

        List<ChatSessionMessage> descending = new ArrayList<>(
                messageRepository.findPageBeforeSeq(sessionId, historyUpperExclusive, normalizedLimit));
        if (descending.size() < normalizedLimit) {
            descending.addAll(loadArchivedMessagesDescending(
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
        appendMessagesInternal(sessionId, normalizePayloads(messages));
        maybeArchiveSession(sessionId);
        return getSessionDetailOwned(sessionId);
    }

    @Transactional
    public ChatSessionDetail updateMessages(Long sessionId, List<ChatSessionMessagePayload> messages) {
        ChatSession session = getOwnedSessionHeader(sessionId);
        List<ChatSessionMessagePayload> normalized = normalizePayloads(messages);
        int existingCount = safeInt(session.getMessageCount());
        if (normalized.size() < existingCount) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Legacy session update cannot truncate persisted history");
        }
        if (normalized.size() == existingCount) {
            return getSessionDetailOwned(sessionId);
        }
        List<ChatSessionMessagePayload> delta = normalized.subList(existingCount, normalized.size());
        appendMessagesInternal(sessionId, delta);
        maybeArchiveSession(sessionId);
        return getSessionDetailOwned(sessionId);
    }

    @Transactional
    public ChatSessionDetail endSession(Long sessionId, String summary, String authorization) {
        ChatSession session = getOwnedSessionHeader(sessionId);
        String normalizedSummary = blankToNull(summary);

        LocalDateTime endedAt = LocalDateTime.now();
        int updated = sessionRepository.endActiveSession(sessionId, normalizedSummary, endedAt);
        ChatSession endedSession = getOwnedSessionHeader(sessionId);

        if (updated == 0) {
            if (STATUS_ENDED.equals(endedSession.getStatus()) && endedSession.getEndedAt() == null) {
                sessionRepository.fillMissingEndedAt(sessionId, endedAt);
                endedSession.setEndedAt(endedAt);
            }
            maybeArchiveSession(sessionId);
            return getSessionDetailOwned(sessionId);
        }

        endedSession.setStatus(STATUS_ENDED);
        if (normalizedSummary != null) {
            endedSession.setSummary(normalizedSummary);
            sessionRepository.updateById(endedSession);
        }
        endedSession.setEndedAt(endedAt);
        maybeArchiveSession(sessionId);
        return getSessionDetailOwned(sessionId);
    }

    public ChatSessionDetail endSession(Long sessionId, String summary) {
        return endSession(sessionId, summary, null);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        ChatSession session = getOwnedSessionHeader(sessionId);
        sessionRepository.deleteById(session.getId());
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
        List<ChatSessionMessageItem> transcript = archiveStorageService.readTranscript(archive.getObjectKey()).stream()
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
                .metadata(castMap(archive.getMetadata()))
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
        Map<String, Object> metadata = withStorageVersion(toMutableMap(header.getMetadata()));
        List<ChatSessionMessagePayload> payloads = parseLegacyMessages(header.getMessages());
        if (payloads.isEmpty()) {
            header.setMessageCount(Math.max(safeInt(header.getMessageCount()), 0));
            header.setTokenCount(Math.max(safeInt(header.getTokenCount()), 0));
            header.setMetadata(metadata);
            header.setArchiveMetadata(toMutableMap(header.getArchiveMetadata()));
            header.setLastMessageAt(header.getLastMessageAt() == null ? header.getStartedAt() : header.getLastMessageAt());
            sessionRepository.updateById(header);
            return;
        }

        Integer maxSeq = messageRepository.findMaxSeqBySessionId(header.getId());
        if (maxSeq != null && maxSeq > 0) {
            header.setMetadata(metadata);
            header.setTitle(deriveTitle(header, messageRepository.findBySessionId(header.getId())));
            header.setLastMessageAt(resolveLastMessageAt(messageRepository.findBySessionId(header.getId()), header));
            header.setMessageCount(Math.max(safeInt(header.getMessageCount()), maxSeq));
            sessionRepository.updateById(header);
            return;
        }

        appendMessagesInternal(header.getId(), payloads);
        ChatSession refreshed = sessionRepository.selectById(header.getId());
        refreshed.setMetadata(metadata);
        sessionRepository.updateById(refreshed);
        maybeArchiveSession(header.getId());
    }

    private void appendMessagesInternal(Long sessionId, List<ChatSessionMessagePayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }
        ChatSession session = sessionRepository.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Integer maxSeq = messageRepository.findMaxSeqBySessionId(sessionId);
        int nextSeq = maxSeq == null ? 1 : maxSeq + 1;

        List<ChatSessionMessage> persisted = new ArrayList<>();
        for (ChatSessionMessagePayload payload : payloads) {
            ChatSessionMessage entity = new ChatSessionMessage();
            entity.setSessionId(sessionId);
            entity.setSeq(nextSeq++);
            entity.setClientMessageId(blankToNull(payload.getId()));
            entity.setRole(normalizeRole(payload.getRole()));
            entity.setContent(String.valueOf(payload.getContent() == null ? "" : payload.getContent()));
            entity.setToolName(blankToNull(payload.getToolName()));
            entity.setMetadata(toMutableMap(payload.getMetadata()));
            entity.setCreatedAt(parseTimestamp(payload.getTimestamp(), session.getStartedAt()));
            entity.setTokenCount(Math.max(payload.getTokenCount() == null ? 0 : payload.getTokenCount(), 0));
            messageRepository.insert(entity);
            persisted.add(entity);
        }

        List<ChatSessionMessage> allMessages = messageRepository.findBySessionId(sessionId);
        session.setTitle(deriveTitle(session, allMessages));
        if (blankToNull(session.getSummary()) == null) {
            session.setSummary(buildRollingSummary(allMessages));
        }
        session.setLastMessageAt(resolveLastMessageAt(allMessages, session));
        session.setMessageCount(safeInt(session.getMessageCount()) + persisted.size());
        session.setTokenCount(safeInt(session.getTokenCount()) + persisted.stream()
                .map(ChatSessionMessage::getTokenCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
        session.setMetadata(withStorageVersion(toMutableMap(session.getMetadata())));
        if (session.getArchiveMetadata() == null) {
            session.setArchiveMetadata(new HashMap<>());
        }
        sessionRepository.updateById(session);
    }

    private void maybeArchiveSession(Long sessionId) {
        ChatSession session = sessionRepository.selectById(sessionId);
        if (session == null) {
            return;
        }
        while (safeInt(session.getMessageCount()) > ARCHIVE_TRIGGER_MESSAGE_COUNT) {
            Integer maxSeq = messageRepository.findMaxSeqBySessionId(sessionId);
            if (maxSeq == null || maxSeq <= ARCHIVE_RETAIN_RECENT_COUNT) {
                return;
            }
            int archivedBeforeSeq = safeInt(session.getArchivedBeforeSeq());
            int highestArchivableSeq = maxSeq - ARCHIVE_RETAIN_RECENT_COUNT;
            int startSeq = archivedBeforeSeq + 1;
            if (highestArchivableSeq < startSeq) {
                return;
            }
            int endSeq = Math.min(startSeq + ARCHIVE_CHUNK_SIZE - 1, highestArchivableSeq);
            List<ChatSessionMessage> chunk = messageRepository.findBySessionIdAndSeqRange(sessionId, startSeq, endSeq);
            if (chunk.isEmpty()) {
                return;
            }

            Map<String, Object> summaryData = archiveSummaryService.summarize(session, chunk);
            String objectKey = archiveStorageService.writeTranscript(sessionId, startSeq, endSeq, chunk);

            ChatSessionArchive archive = new ChatSessionArchive();
            archive.setSessionId(sessionId);
            archive.setStartSeq(startSeq);
            archive.setEndSeq(endSeq);
            archive.setSummary(stringValue(summaryData.get("summary"), buildRollingSummary(chunk)));
            archive.setObjectKey(objectKey);
            archive.setMessageCount(chunk.size());
            archive.setTokenCount(chunk.stream().map(ChatSessionMessage::getTokenCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
            archive.setCreatedAt(LocalDateTime.now());
            archive.setMetadata(new LinkedHashMap<>(summaryData));
            archiveRepository.insert(archive);

            messageRepository.deleteSeqRange(sessionId, startSeq, endSeq);

            session = sessionRepository.selectById(sessionId);
            session.setArchivedBeforeSeq(endSeq);
            session.setArchiveStatus("READY");
            if (blankToNull(session.getTitle()) == null) {
                session.setTitle(blankToNull(stringValue(summaryData.get("titleSuggestion"), "")));
            }
            if (blankToNull(session.getSummary()) == null) {
                session.setSummary(archive.getSummary());
            }
            Map<String, Object> archiveMetadata = toMutableMap(session.getArchiveMetadata());
            archiveMetadata.put("lastArchiveId", archive.getId());
            archiveMetadata.put("lastArchiveAt", archive.getCreatedAt().toString());
            archiveMetadata.put("lastArchiveRange", startSeq + "-" + endSeq);
            archiveMetadata.put("storageVersion", STORAGE_VERSION);
            session.setArchiveMetadata(archiveMetadata);
            session.setMetadata(withStorageVersion(toMutableMap(session.getMetadata())));
            sessionRepository.updateById(session);
        }
    }

    private List<ChatSessionMessage> loadArchivedMessagesDescending(Long sessionId, long beforeSeq, int remaining) {
        if (remaining <= 0 || beforeSeq <= 1) {
            return List.of();
        }

        List<ChatSessionMessage> collected = new ArrayList<>();
        List<ChatSessionArchive> archives = archiveRepository.findRangesBeforeSeqDesc(
                sessionId,
                beforeSeq,
                Math.max(remaining, 4));
        for (ChatSessionArchive archive : archives) {
            List<ChatSessionMessage> transcript = archiveStorageService.readTranscript(archive.getObjectKey());
            if (transcript.isEmpty()) {
                continue;
            }
            List<ChatSessionMessage> eligible = transcript.stream()
                    .filter(message -> message.getSeq() != null && message.getSeq() < beforeSeq)
                    .sorted((left, right) -> Integer.compare(right.getSeq(), left.getSeq()))
                    .toList();
            for (ChatSessionMessage message : eligible) {
                collected.add(message);
                if (collected.size() >= remaining) {
                    return collected;
                }
            }
        }
        return collected;
    }

    private long resolveHistoryUpperExclusive(ChatSession session, Long beforeSeq) {
        if (beforeSeq != null && beforeSeq > 0) {
            return beforeSeq;
        }
        int totalMessages = safeInt(session.getMessageCount());
        return totalMessages <= 0 ? 1L : (long) totalMessages + 1L;
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
                .metadata(castMap(session.getMetadata()))
                .archiveMetadata(castMap(session.getArchiveMetadata()))
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
                .metadata(castMap(session.getMetadata()))
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
                .metadata(castMap(archive.getMetadata()))
                .build();
    }

    private ChatSessionMessageItem toMessageItem(ChatSessionMessage message) {
        return ChatSessionMessageItem.builder()
                .seq(message.getSeq() == null ? null : message.getSeq().longValue())
                .id(message.getClientMessageId())
                .role(message.getRole())
                .content(message.getContent())
                .toolName(message.getToolName())
                .metadata(castMap(message.getMetadata()))
                .createdAt(message.getCreatedAt())
                .tokenCount(message.getTokenCount())
                .build();
    }

    private List<ChatSessionMessagePayload> parseLegacyMessages(Object rawMessages) {
        if (rawMessages == null) {
            return List.of();
        }
        List<?> list = objectMapper.convertValue(rawMessages, List.class);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<ChatSessionMessagePayload> payloads = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {});
            ChatSessionMessagePayload payload = new ChatSessionMessagePayload();
            payload.setId(stringValue(map.get("id"), null));
            payload.setRole(stringValue(map.get("role"), "user"));
            payload.setContent(stringValue(map.get("content"), ""));
            payload.setTimestamp(stringValue(map.get("timestamp"), null));
            Object metadata = map.get("metadata");
            if (metadata instanceof Map<?, ?> nested) {
                payload.setMetadata(new LinkedHashMap<>(castMap(nested)));
            }
            payloads.add(payload);
        }
        return payloads;
    }

    private List<ChatSessionMessagePayload> normalizePayloads(List<ChatSessionMessagePayload> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ChatSessionMessagePayload> normalized = new ArrayList<>();
        for (ChatSessionMessagePayload message : messages) {
            if (message == null) {
                continue;
            }
            ChatSessionMessagePayload copy = new ChatSessionMessagePayload();
            copy.setId(blankToNull(message.getId()));
            copy.setRole(normalizeRole(message.getRole()));
            copy.setContent(message.getContent() == null ? "" : message.getContent());
            copy.setTimestamp(blankToNull(message.getTimestamp()));
            copy.setToolName(blankToNull(message.getToolName()));
            copy.setTokenCount(message.getTokenCount() == null ? 0 : Math.max(message.getTokenCount(), 0));
            copy.setMetadata(message.getMetadata() == null ? Map.of() : new LinkedHashMap<>(message.getMetadata()));
            normalized.add(copy);
        }
        return normalized;
    }

    private String deriveTitle(ChatSession session, List<ChatSessionMessage> messages) {
        String existing = blankToNull(session.getTitle());
        if (existing != null) {
            return existing;
        }
        Map<String, Object> metadata = castMap(session.getMetadata());
        Object questionContent = metadata.get("questionContent");
        if (questionContent instanceof Map<?, ?> map) {
            String stem = blankToNull(String.valueOf(map.get("stem")));
            if (stem != null) {
                return truncate(stem, 36);
            }
        } else if (questionContent instanceof String stem) {
            String title = blankToNull(stem);
            if (title != null) {
                return truncate(title, 36);
            }
        }
        for (ChatSessionMessage message : messages) {
            if ("user".equalsIgnoreCase(message.getRole()) && blankToNull(message.getContent()) != null) {
                return truncate(message.getContent().trim(), 36);
            }
        }
        return truncate(blankToNull(session.getSummary()) == null ? "未命名会话" : session.getSummary(), 36);
    }

    private LocalDateTime resolveLastMessageAt(List<ChatSessionMessage> messages, ChatSession session) {
        if (!messages.isEmpty()) {
            ChatSessionMessage last = messages.get(messages.size() - 1);
            if (last.getCreatedAt() != null) {
                return last.getCreatedAt();
            }
        }
        return session.getLastMessageAt() == null ? session.getStartedAt() : session.getLastMessageAt();
    }

    private String buildRollingSummary(List<ChatSessionMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index -= 1) {
            String content = blankToNull(messages.get(index).getContent());
            if (content != null) {
                return truncate(content, 120);
            }
        }
        return null;
    }

    private Map<String, Object> withStorageVersion(Map<String, Object> metadata) {
        metadata.put("storageVersion", STORAGE_VERSION);
        return metadata;
    }

    private static int normalizeSessionLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_SESSION_LIMIT;
        }
        return Math.min(limit, MAX_SESSION_LIMIT);
    }

    private static int normalizeMessagePageSize(int limit) {
        if (limit <= 0) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        return Math.min(limit, MAX_MESSAGE_PAGE_SIZE);
    }

    private static String normalizeRole(String role) {
        String normalized = role == null ? "user" : role.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "assistant", "system", "user" -> normalized;
            default -> "user";
        };
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxLength));
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> toMutableMap(Object value) {
        return castMap(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static LocalDateTime parseTimestamp(String value, LocalDateTime fallback) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return fallback == null ? LocalDateTime.now() : fallback;
        }
        try {
            return OffsetDateTime.parse(normalized).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Try local date-time below.
        }
        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            return fallback == null ? LocalDateTime.now() : fallback;
        }
    }
}
