package com.familyagent.module.session.service;

import com.familyagent.module.session.dto.ChatSessionArchiveMetadata;
import com.familyagent.module.session.dto.ChatSessionArchiveSummaryData;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.entity.ChatSessionArchive;
import com.familyagent.module.session.entity.ChatSessionMessage;
import com.familyagent.module.session.repository.ChatSessionArchiveRepository;
import com.familyagent.module.session.repository.ChatSessionMessageRepository;
import com.familyagent.module.session.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
class ChatSessionArchiveSupport {

    private static final int ARCHIVE_TRIGGER_MESSAGE_COUNT = 100;
    private static final int ARCHIVE_CHUNK_SIZE = 50;
    private static final int ARCHIVE_RETAIN_RECENT_COUNT = 30;
    private static final int STORAGE_VERSION = 2;

    private final ChatSessionRepository sessionRepository;
    private final ChatSessionMessageRepository messageRepository;
    private final ChatSessionArchiveRepository archiveRepository;
    private final ChatSessionArchiveStorageService archiveStorageService;
    private final ChatSessionArchiveSummaryService archiveSummaryService;

    void maybeArchiveSession(Long sessionId) {
        ChatSession session = sessionRepository.selectById(sessionId);
        if (session == null) {
            return;
        }
        while (ChatSessionSupportUtils.safeInt(session.getMessageCount()) > ARCHIVE_TRIGGER_MESSAGE_COUNT) {
            Integer maxSeq = messageRepository.findMaxSeqBySessionId(sessionId);
            if (maxSeq == null || maxSeq <= ARCHIVE_RETAIN_RECENT_COUNT) {
                return;
            }
            int archivedBeforeSeq = ChatSessionSupportUtils.safeInt(session.getArchivedBeforeSeq());
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

            ChatSessionArchiveSummaryData summaryData = archiveSummaryService.summarize(session, chunk);
            String objectKey = archiveStorageService.writeTranscript(sessionId, startSeq, endSeq, chunk);

            ChatSessionArchive archive = new ChatSessionArchive();
            archive.setSessionId(sessionId);
            archive.setStartSeq(startSeq);
            archive.setEndSeq(endSeq);
            archive.setSummary(ChatSessionSupportUtils.stringValue(
                    summaryData.summary(),
                    ChatSessionSupportUtils.buildRollingSummary(chunk)));
            archive.setObjectKey(objectKey);
            archive.setMessageCount(chunk.size());
            archive.setTokenCount(chunk.stream().map(ChatSessionMessage::getTokenCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
            archive.setCreatedAt(LocalDateTime.now());
            archive.setMetadata(summaryData.toMetadataMap());
            archiveRepository.insert(archive);

            messageRepository.deleteSeqRange(sessionId, startSeq, endSeq);

            session = sessionRepository.selectById(sessionId);
            session.setArchivedBeforeSeq(endSeq);
            session.setArchiveStatus("READY");
            if (ChatSessionSupportUtils.blankToNull(session.getTitle()) == null) {
                session.setTitle(ChatSessionSupportUtils.blankToNull(
                        ChatSessionSupportUtils.stringValue(summaryData.titleSuggestion(), "")));
            }
            if (ChatSessionSupportUtils.blankToNull(session.getSummary()) == null) {
                session.setSummary(archive.getSummary());
            }
            Map<String, Object> archiveMetadata = ChatSessionSupportUtils.archiveMetadataToMap(
                    new ChatSessionArchiveMetadata(
                            archive.getId(),
                            archive.getCreatedAt().toString(),
                            startSeq + "-" + endSeq,
                            STORAGE_VERSION));
            session.setArchiveMetadata(archiveMetadata);
            session.setMetadata(ChatSessionSupportUtils.withStorageVersion(
                    ChatSessionSupportUtils.toMutableMap(session.getMetadata()),
                    STORAGE_VERSION));
            sessionRepository.updateById(session);
        }
    }

    List<ChatSessionMessage> loadArchivedMessagesDescending(Long sessionId, long beforeSeq, int remaining) {
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

    List<ChatSessionMessage> readArchiveTranscript(String objectKey) {
        return archiveStorageService.readTranscript(objectKey);
    }

    long resolveHistoryUpperExclusive(ChatSession session, Long beforeSeq) {
        if (beforeSeq != null && beforeSeq > 0) {
            return beforeSeq;
        }
        int totalMessages = ChatSessionSupportUtils.safeInt(session.getMessageCount());
        return totalMessages <= 0 ? 1L : (long) totalMessages + 1L;
    }
}
