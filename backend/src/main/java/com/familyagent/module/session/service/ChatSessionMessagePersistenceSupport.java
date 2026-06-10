package com.familyagent.module.session.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.session.dto.ChatSessionMessagePayload;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.entity.ChatSessionMessage;
import com.familyagent.module.session.repository.ChatSessionMessageRepository;
import com.familyagent.module.session.repository.ChatSessionRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

final class ChatSessionMessagePersistenceSupport {

    private final ChatSessionRepository sessionRepository;
    private final ChatSessionMessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final int storageVersion;

    ChatSessionMessagePersistenceSupport(ChatSessionRepository sessionRepository,
                                         ChatSessionMessageRepository messageRepository,
                                         ObjectMapper objectMapper,
                                         int storageVersion) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.storageVersion = storageVersion;
    }

    void appendMessagesInternal(Long sessionId, List<ChatSessionMessagePayload> payloads) {
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
            entity.setClientMessageId(ChatSessionSupportUtils.blankToNull(payload.getId()));
            entity.setRole(ChatSessionSupportUtils.normalizeRole(payload.getRole()));
            entity.setContent(String.valueOf(payload.getContent() == null ? "" : payload.getContent()));
            entity.setToolName(ChatSessionSupportUtils.blankToNull(payload.getToolName()));
            entity.setMetadata(ChatSessionSupportUtils.toMutableMap(payload.getMetadata()));
            entity.setCreatedAt(ChatSessionSupportUtils.parseTimestamp(payload.getTimestamp(), session.getStartedAt()));
            entity.setTokenCount(Math.max(payload.getTokenCount() == null ? 0 : payload.getTokenCount(), 0));
            messageRepository.insert(entity);
            persisted.add(entity);
        }

        List<ChatSessionMessage> allMessages = messageRepository.findBySessionId(sessionId);
        session.setTitle(ChatSessionSupportUtils.deriveTitle(session, allMessages));
        if (ChatSessionSupportUtils.blankToNull(session.getSummary()) == null) {
            session.setSummary(ChatSessionSupportUtils.buildRollingSummary(allMessages));
        }
        session.setLastMessageAt(ChatSessionSupportUtils.resolveLastMessageAt(allMessages, session));
        session.setMessageCount(ChatSessionSupportUtils.safeInt(session.getMessageCount()) + persisted.size());
        session.setTokenCount(ChatSessionSupportUtils.safeInt(session.getTokenCount()) + persisted.stream()
                .map(ChatSessionMessage::getTokenCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
        session.setMetadata(ChatSessionSupportUtils.withStorageVersion(
                ChatSessionSupportUtils.toMutableMap(session.getMetadata()),
                storageVersion));
        if (session.getArchiveMetadata() == null) {
            session.setArchiveMetadata(new HashMap<>());
        }
        sessionRepository.updateById(session);
    }

    List<ChatSessionMessagePayload> parseLegacyMessages(Object rawMessages) {
        return ChatSessionSupportUtils.parseLegacyMessages(rawMessages, objectMapper);
    }

    List<ChatSessionMessagePayload> normalizePayloads(List<ChatSessionMessagePayload> messages) {
        return ChatSessionSupportUtils.normalizePayloads(messages);
    }
}
