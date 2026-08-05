package com.familyagent.module.session.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.session.dto.ChatSessionDetail;
import com.familyagent.module.session.dto.ChatSessionMessagePage;
import com.familyagent.module.session.dto.ChatSessionMessagePayload;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.entity.ChatSessionArchive;
import com.familyagent.module.session.entity.ChatSessionMessage;
import com.familyagent.module.session.repository.ChatSessionArchiveRepository;
import com.familyagent.module.session.repository.ChatSessionMessageRepository;
import com.familyagent.module.session.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock private ChatSessionRepository sessionRepository;
    @Mock private ChatSessionMessageRepository messageRepository;
    @Mock private ChatSessionArchiveRepository archiveRepository;
    @Mock private FamilyMembershipFacade familyMembershipFacade;
    @Mock private ChatSessionArchiveStorageService archiveStorageService;
    @Mock private ChatSessionArchiveSummaryService archiveSummaryService;

    private ChatSessionService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ChatSessionMessagePersistenceSupport messagePersistenceSupport =
                new ChatSessionMessagePersistenceSupport(sessionRepository, messageRepository, objectMapper);
        ChatSessionArchiveSupport archiveSupport =
                new ChatSessionArchiveSupport(sessionRepository, messageRepository, archiveRepository,
                        archiveStorageService, archiveSummaryService);
        service = new ChatSessionService(sessionRepository, messageRepository, archiveRepository,
                familyMembershipFacade, messagePersistenceSupport, archiveSupport, archiveStorageService);
    }

    @Test
    void endSession_shouldBeIdempotentAndRepairMissingEndedAt() {
        ChatSession ended = sessionHeader(100L, 10L, "ENDED");
        ended.setEndedAt(null);

        when(sessionRepository.findHeaderById(100L)).thenReturn(ended, ended, ended);
        when(sessionRepository.endActiveSession(eq(100L), eq(null), any(LocalDateTime.class))).thenReturn(0);
        when(sessionRepository.selectById(100L)).thenReturn(ended);
        when(archiveRepository.findBySessionId(100L)).thenReturn(List.of());

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            ChatSessionDetail result = service.endSession(100L, " ", null);

            assertEquals("ENDED", result.getStatus());
            assertNotNull(result.getEndedAt());
        }

        verify(sessionRepository).fillMissingEndedAt(eq(100L), any(LocalDateTime.class));
        verify(sessionRepository, never()).updateById(any(ChatSession.class));
    }

    @Test
    void updateMessages_shouldAppendOnlyNewTailMessages() {
        ChatSession session = sessionHeader(100L, 10L, "ACTIVE");
        session.setStartedAt(LocalDateTime.of(2026, 6, 9, 10, 0));
        session.setLastMessageAt(session.getStartedAt());
        session.setMessageCount(1);
        session.setTokenCount(3);
        session.setMetadata(Map.of("entry", "agent"));

        ChatSessionMessage first = message(1, "user", "hello");
        ChatSessionMessage second = message(2, "assistant", "world");

        when(sessionRepository.findHeaderById(100L)).thenReturn(session, session);
        when(sessionRepository.selectById(100L)).thenReturn(session, session);
        when(sessionRepository.updateById(any(ChatSession.class))).thenReturn(1);
        when(messageRepository.findMaxSeqBySessionId(100L)).thenReturn(1);
        when(messageRepository.findBySessionId(100L)).thenReturn(List.of(first, second));
        when(archiveRepository.findBySessionId(100L)).thenReturn(List.of());

        ChatSessionMessagePayload existing = payload("m1", "user", "hello");
        ChatSessionMessagePayload appended = payload("m2", "assistant", "world");

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            ChatSessionDetail result = service.updateMessages(100L, List.of(existing, appended));

            assertEquals(2, result.getMessageCount());
            assertEquals("hello", result.getTitle());
        }

        ArgumentCaptor<ChatSessionMessage> captor = ArgumentCaptor.forClass(ChatSessionMessage.class);
        verify(messageRepository).insert(captor.capture());
        assertEquals(2, captor.getValue().getSeq());
        assertEquals("assistant", captor.getValue().getRole());
        assertEquals("world", captor.getValue().getContent());
        verify(messageRepository, never()).deleteSeqRange(anyLong(), anyInt(), anyInt());
    }

    @Test
    void getSessionMessages_returnsOnlyLiveMessagesWhenNoArchivesExist() {
        ChatSession session = sessionWithMessageCount(6);
        List<ChatSessionMessage> liveMessages = List.of(
                message(4, "user", "m4"),
                message(5, "assistant", "m5"),
                message(6, "user", "m6"));

        when(sessionRepository.findHeaderById(100L)).thenReturn(session);
        when(messageRepository.findPageBeforeSeq(100L, 7L, 3)).thenReturn(liveMessages);

        ChatSessionMessagePage page = withUser(10L, () -> service.getSessionMessages(100L, null, 3));

        assertEquals(List.of(4L, 5L, 6L), page.getItems().stream().map(item -> item.getSeq()).toList());
        assertTrue(page.isHasMore());
        assertEquals(4L, page.getNextBeforeSeq());
        verify(archiveRepository, never()).findRangesBeforeSeqDesc(anyLong(), anyLong(), anyInt());
    }

    @Test
    void getSessionMessages_returnsArchiveOnlyHistoryWhenEarlyMessagesWereArchived() {
        ChatSession session = sessionWithMessageCount(6);
        session.setArchivedBeforeSeq(4);
        ChatSessionArchive archive = archive(1000L, 1, 4, "archive-1");

        when(sessionRepository.findHeaderById(100L)).thenReturn(session);
        when(messageRepository.findPageBeforeSeq(100L, 5L, 4)).thenReturn(List.of());
        when(archiveRepository.findRangesBeforeSeqDesc(100L, 5L, 4)).thenReturn(List.of(archive));
        when(archiveStorageService.readTranscript("archive-1")).thenReturn(List.of(
                message(1, "user", "a1"),
                message(2, "assistant", "a2"),
                message(3, "user", "a3"),
                message(4, "assistant", "a4")));

        ChatSessionMessagePage page = withUser(10L, () -> service.getSessionMessages(100L, 5L, 4));

        assertEquals(List.of(1L, 2L, 3L, 4L), page.getItems().stream().map(item -> item.getSeq()).toList());
        assertFalse(page.isHasMore());
        assertNull(page.getNextBeforeSeq());
    }

    @Test
    void getSessionMessages_mergesLiveAndArchivedHistoryInAscendingSeqOrder() {
        ChatSession session = sessionWithMessageCount(8);
        session.setArchivedBeforeSeq(5);
        ChatSessionArchive archive = archive(1001L, 1, 5, "archive-5");

        when(sessionRepository.findHeaderById(100L)).thenReturn(session);
        when(messageRepository.findPageBeforeSeq(100L, 9L, 5)).thenReturn(List.of(
                message(8, "assistant", "m8"),
                message(7, "user", "m7"),
                message(6, "assistant", "m6")));
        when(archiveRepository.findRangesBeforeSeqDesc(100L, 9L, 4)).thenReturn(List.of(archive));
        when(archiveStorageService.readTranscript("archive-5")).thenReturn(List.of(
                message(1, "user", "m1"),
                message(2, "assistant", "m2"),
                message(3, "user", "m3"),
                message(4, "assistant", "m4"),
                message(5, "user", "m5")));

        ChatSessionMessagePage page = withUser(10L, () -> service.getSessionMessages(100L, null, 5));

        assertEquals(List.of(4L, 5L, 6L, 7L, 8L), page.getItems().stream().map(item -> item.getSeq()).toList());
        assertTrue(page.isHasMore());
        assertEquals(4L, page.getNextBeforeSeq());
    }

    @Test
    void getSessionMessages_computesHasMoreAcrossArchiveLiveBoundary() {
        ChatSession session = sessionWithMessageCount(7);
        session.setArchivedBeforeSeq(4);
        ChatSessionArchive archive = archive(1002L, 1, 4, "archive-4");

        when(sessionRepository.findHeaderById(100L)).thenReturn(session);
        when(messageRepository.findPageBeforeSeq(100L, 6L, 3)).thenReturn(List.of(
                message(5, "assistant", "m5")));
        when(archiveRepository.findRangesBeforeSeqDesc(100L, 6L, 4)).thenReturn(List.of(archive));
        when(archiveStorageService.readTranscript("archive-4")).thenReturn(List.of(
                message(1, "user", "m1"),
                message(2, "assistant", "m2"),
                message(3, "user", "m3"),
                message(4, "assistant", "m4")));

        ChatSessionMessagePage page = withUser(10L, () -> service.getSessionMessages(100L, 6L, 3));

        assertEquals(List.of(3L, 4L, 5L), page.getItems().stream().map(item -> item.getSeq()).toList());
        assertTrue(page.isHasMore());
        assertEquals(3L, page.getNextBeforeSeq());
    }

    @Test
    void appendMessages_updatesSessionMetadataFromContextPatch() {
        ChatSession session = sessionHeader(100L, 10L, "ACTIVE");
        session.setStartedAt(LocalDateTime.of(2026, 6, 9, 10, 0));
        session.setLastMessageAt(session.getStartedAt());
        session.setMetadata(Map.of("entry", "agent", "agentMode", "family", "storageVersion", 2));

        ChatSessionMessage first = message(1, "user", "hello");
        ChatSessionMessage switchMarker = message(2, "system", "switched");
        switchMarker.setMetadata(Map.of(
                "agentMode", "mirror",
                "sessionContextPatch", Map.of(
                        "agentMode", "mirror",
                        "targetUserId", 22,
                        "targetMemberName", "Mom",
                        "hasTargetSwitches", true)));

        when(sessionRepository.findHeaderById(100L)).thenReturn(session, session);
        when(sessionRepository.selectById(100L)).thenReturn(session, session);
        when(sessionRepository.updateById(any(ChatSession.class))).thenReturn(1);
        when(messageRepository.findMaxSeqBySessionId(100L)).thenReturn(1);
        when(messageRepository.findBySessionId(100L)).thenReturn(List.of(first, switchMarker));
        when(archiveRepository.findBySessionId(100L)).thenReturn(List.of());

        ChatSessionMessagePayload payload = payload("m2", "system", "switched");
        payload.setMetadata(Map.of(
                "agentMode", "mirror",
                "sessionContextPatch", Map.of(
                        "agentMode", "mirror",
                        "targetUserId", 22,
                        "targetMemberName", "Mom",
                        "hasTargetSwitches", true)));

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            ChatSessionDetail result = service.appendMessages(100L, List.of(payload));

            assertEquals("mirror", result.getMetadata().get("agentMode"));
            assertEquals(22, result.getMetadata().get("targetUserId"));
            assertEquals("Mom", result.getMetadata().get("targetMemberName"));
            assertEquals(true, result.getMetadata().get("hasTargetSwitches"));
        }

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionRepository).updateById(captor.capture());
        Map<String, Object> persistedMetadata = ChatSessionSupportUtils.castMap(captor.getValue().getMetadata());
        assertEquals("mirror", persistedMetadata.get("agentMode"));
    }

    @Test
    void appendMessagesInternal_persistsAnswerEvidenceMetadata() {
        ChatSession session = sessionHeader(100L, 10L, "ACTIVE");
        session.setStartedAt(LocalDateTime.of(2026, 6, 9, 10, 0));
        session.setLastMessageAt(session.getStartedAt());
        session.setMetadata(Map.of("entry", "agent", "storageVersion", 2));
        ChatSessionMessage stored = message(1, "assistant", "answer");

        when(sessionRepository.selectById(100L)).thenReturn(session);
        when(messageRepository.findMaxSeqBySessionId(100L)).thenReturn(0);
        when(messageRepository.findBySessionId(100L)).thenReturn(List.of(stored));
        when(sessionRepository.updateById(any(ChatSession.class))).thenReturn(1);

        ChatSessionMessagePayload payload = payload("assistant-1", "assistant", "answer");
        payload.setMetadata(Map.of(
                "agentMode", "mirror",
                "targetUserId", 202,
                "targetMemberName", "大儿子",
                "rag", Map.of(
                        "memoryCount", 1,
                        "totalReferenceCount", 1,
                        "sources", List.of(Map.of(
                                "id", "personal-9",
                                "sourceType", "PERSONAL_MEMORY",
                                "author", Map.of(
                                        "userId", 303,
                                        "relationshipToViewer", "哥哥",
                                        "currentViewer", false))))));

        ChatSessionMessagePersistenceSupport support =
                new ChatSessionMessagePersistenceSupport(sessionRepository, messageRepository, new ObjectMapper());
        support.appendMessagesInternal(100L, List.of(payload));

        ArgumentCaptor<ChatSessionMessage> captor = ArgumentCaptor.forClass(ChatSessionMessage.class);
        verify(messageRepository).insert(captor.capture());
        Map<String, Object> persisted = ChatSessionSupportUtils.castMap(captor.getValue().getMetadata());
        assertEquals("大儿子", persisted.get("targetMemberName"));
        Map<String, Object> rag = ChatSessionSupportUtils.castMap(persisted.get("rag"));
        assertEquals(1, rag.get("totalReferenceCount"));
        assertEquals(1, ((List<?>) rag.get("sources")).size());
    }

    @Test
    void applySessionContextPatch_copiesCompletePersonaIdentity() {
        ChatSessionMessagePayload marker = payload("switch-1", "system", "switched");
        marker.setMetadata(Map.of("sessionContextPatch", Map.of(
                "contextLabel", "persona_member",
                "agentMode", "persona",
                "targetUserId", 0,
                "targetPersonaId", 303,
                "targetMemberName", "",
                "targetPersonaName", "外公",
                "hasTargetSwitches", true)));

        Map<String, Object> updated = ChatSessionSupportUtils.applySessionContextPatch(
                new java.util.LinkedHashMap<>(),
                List.of(marker));

        assertEquals("persona_member", updated.get("contextLabel"));
        assertEquals("persona", updated.get("agentMode"));
        assertEquals(303, updated.get("targetPersonaId"));
        assertEquals("外公", updated.get("targetPersonaName"));
        assertEquals(true, updated.get("hasTargetSwitches"));
    }

    @Test
    void deleteSession_deletesArchiveObjectsBeforeSessionRow() {
        ChatSession session = sessionHeader(100L, 10L, "ACTIVE");
        ChatSessionArchive firstArchive = archive(1000L, 1, 4, "archive-1");
        ChatSessionArchive secondArchive = archive(1001L, 5, 8, "archive-2");

        when(sessionRepository.findHeaderById(100L)).thenReturn(session);
        when(archiveRepository.findBySessionId(100L)).thenReturn(List.of(firstArchive, secondArchive));

        withUser(10L, () -> {
            service.deleteSession(100L);
            return null;
        });

        InOrder inOrder = inOrder(sessionRepository, archiveRepository, messageRepository, archiveStorageService);
        inOrder.verify(sessionRepository).findHeaderById(100L);
        inOrder.verify(archiveRepository).findBySessionId(100L);
        inOrder.verify(archiveStorageService).deleteTranscript("archive-1");
        inOrder.verify(archiveStorageService).deleteTranscript("archive-2");
        inOrder.verify(archiveRepository).deleteBySessionId(100L);
        inOrder.verify(messageRepository).deleteBySessionId(100L);
        inOrder.verify(sessionRepository).deleteOwnedById(100L);
    }

    @Test
    void deleteSession_skipsBlankArchiveObjectKeysAndDeletesSessionRow() {
        ChatSession session = sessionHeader(100L, 10L, "ACTIVE");
        ChatSessionArchive nullKeyArchive = archive(1000L, 1, 4, null);
        ChatSessionArchive blankKeyArchive = archive(1001L, 5, 8, " ");

        when(sessionRepository.findHeaderById(100L)).thenReturn(session);
        when(archiveRepository.findBySessionId(100L)).thenReturn(List.of(nullKeyArchive, blankKeyArchive));

        withUser(10L, () -> {
            service.deleteSession(100L);
            return null;
        });

        verify(archiveStorageService, never()).deleteTranscript(any());
        verify(archiveRepository).deleteBySessionId(100L);
        verify(messageRepository).deleteBySessionId(100L);
        verify(sessionRepository).deleteOwnedById(100L);
    }

    @Test
    void deleteSession_doesNotDeleteSessionRowWhenArchiveDeleteFails() {
        ChatSession session = sessionHeader(100L, 10L, "ACTIVE");
        ChatSessionArchive archive = archive(1000L, 1, 4, "archive-1");

        when(sessionRepository.findHeaderById(100L)).thenReturn(session);
        when(archiveRepository.findBySessionId(100L)).thenReturn(List.of(archive));
        doThrow(new RuntimeException("storage down")).when(archiveStorageService).deleteTranscript("archive-1");

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service.deleteSession(100L));
        }

        verify(sessionRepository, never()).deleteOwnedById(100L);
        verify(archiveRepository, never()).deleteBySessionId(anyLong());
        verify(messageRepository, never()).deleteBySessionId(anyLong());
    }

    @Test
    void deleteFamilyAgentSessions_deletesAllOwnedFamilyAgentSessions() {
        ChatSession first = sessionHeader(100L, 10L, "ACTIVE");
        ChatSession second = sessionHeader(101L, 10L, "ENDED");
        second.setFamilyId(1L);
        ChatSessionArchive archive = archive(1000L, 1, 4, "archive-1");

        when(sessionRepository.findFamilyAgentByUserAndFamily(10L, 1L)).thenReturn(List.of(first, second));
        when(archiveRepository.findBySessionId(100L)).thenReturn(List.of(archive));
        when(archiveRepository.findBySessionId(101L)).thenReturn(List.of());

        int deleted = withUser(10L, () -> service.deleteFamilyAgentSessions(1L));

        assertEquals(2, deleted);
        verify(familyMembershipFacade).checkMembership(1L);
        verify(archiveStorageService).deleteTranscript("archive-1");
        verify(sessionRepository).deleteOwnedById(100L);
        verify(sessionRepository).deleteOwnedById(101L);
    }

    private static <T> T withUser(Long userId, java.util.concurrent.Callable<T> action) {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(userId);
            return action.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ChatSession sessionHeader(Long id, Long userId, String status) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setFamilyId(1L);
        session.setSubject("FamilyAgent");
        session.setStatus(status);
        session.setStartedAt(LocalDateTime.now().minusMinutes(5));
        session.setMessageCount(0);
        session.setTokenCount(0);
        session.setArchivedBeforeSeq(0);
        session.setArchiveStatus("NONE");
        return session;
    }

    private static ChatSession sessionWithMessageCount(int messageCount) {
        ChatSession session = sessionHeader(100L, 10L, "ACTIVE");
        session.setMessageCount(messageCount);
        session.setLastMessageAt(LocalDateTime.of(2026, 6, 9, 10, 8));
        return session;
    }

    private static ChatSessionArchive archive(Long id, int startSeq, int endSeq, String objectKey) {
        ChatSessionArchive archive = new ChatSessionArchive();
        archive.setId(id);
        archive.setSessionId(100L);
        archive.setStartSeq(startSeq);
        archive.setEndSeq(endSeq);
        archive.setObjectKey(objectKey);
        archive.setMessageCount(endSeq - startSeq + 1);
        archive.setCreatedAt(LocalDateTime.of(2026, 6, 9, 9, 0));
        return archive;
    }

    private static ChatSessionMessage message(int seq, String role, String content) {
        ChatSessionMessage message = new ChatSessionMessage();
        message.setSeq(seq);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.of(2026, 6, 9, 10, seq));
        message.setTokenCount(0);
        return message;
    }

    private static ChatSessionMessagePayload payload(String id, String role, String content) {
        ChatSessionMessagePayload payload = new ChatSessionMessagePayload();
        payload.setId(id);
        payload.setRole(role);
        payload.setContent(content);
        payload.setTimestamp("2026-06-09T10:00:00Z");
        return payload;
    }
}
