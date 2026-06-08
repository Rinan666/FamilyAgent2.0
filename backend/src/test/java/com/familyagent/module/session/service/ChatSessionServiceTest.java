package com.familyagent.module.session.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.service.MemoryService;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock private ChatSessionRepository sessionRepository;
    @Mock private FamilyService familyService;
    @Mock private AIServiceClient aiServiceClient;
    @Mock private MemoryService memoryService;

    @Test
    void endSession_shouldTransitionActiveSessionAndExtractMemoriesOnce() {
        ChatSession active = session(100L, 10L, "ACTIVE", null);
        active.setMessages(List.of(Map.of("role", "user", "content", "need help")));
        ChatSession ended = session(100L, 10L, "ENDED", LocalDateTime.now());
        ended.setMessages(active.getMessages());

        when(sessionRepository.selectById(100L)).thenReturn(active, ended);
        when(sessionRepository.endActiveSession(eq(100L), eq("done"), any(LocalDateTime.class))).thenReturn(1);
        when(aiServiceClient.extractMemories(any(), eq("Bearer token"))).thenReturn(Map.of("memories", List.of()));

        ChatSessionService service = new ChatSessionService(
                sessionRepository, familyService, aiServiceClient, memoryService);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            ChatSession result = service.endSession(100L, "done", "Bearer token");

            assertEquals("ENDED", result.getStatus());
            assertEquals("done", result.getSummary());
            assertNotNull(result.getEndedAt());
        }

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiServiceClient).extractMemories(requestCaptor.capture(), eq("Bearer token"));
        assertEquals(100L, requestCaptor.getValue().get("session_id"));
    }

    @Test
    void endSession_shouldBeIdempotentWhenSessionAlreadyEnded() {
        LocalDateTime endedAt = LocalDateTime.now().minusMinutes(5);
        ChatSession ended = session(100L, 10L, "ENDED", endedAt);

        when(sessionRepository.selectById(100L)).thenReturn(ended, ended);
        when(sessionRepository.endActiveSession(eq(100L), eq(null), any(LocalDateTime.class))).thenReturn(0);

        ChatSessionService service = new ChatSessionService(
                sessionRepository, familyService, aiServiceClient, memoryService);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            ChatSession result = service.endSession(100L, null, "Bearer token");

            assertEquals("ENDED", result.getStatus());
            assertEquals(endedAt, result.getEndedAt());
        }

        verify(aiServiceClient, never()).extractMemories(any(), any());
        verify(sessionRepository, never()).fillMissingEndedAt(any(), any());
    }

    @Test
    void endSession_shouldRepairEndedSessionWithoutEndedAt() {
        ChatSession endedWithoutTime = session(100L, 10L, "ENDED", null);

        when(sessionRepository.selectById(100L)).thenReturn(endedWithoutTime, endedWithoutTime);
        when(sessionRepository.endActiveSession(eq(100L), eq(null), any(LocalDateTime.class))).thenReturn(0);

        ChatSessionService service = new ChatSessionService(
                sessionRepository, familyService, aiServiceClient, memoryService);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            ChatSession result = service.endSession(100L, " ", null);

            assertEquals("ENDED", result.getStatus());
            assertNotNull(result.getEndedAt());
        }

        verify(sessionRepository).fillMissingEndedAt(eq(100L), any(LocalDateTime.class));
        verify(aiServiceClient, never()).extractMemories(any(), any());
    }

    private static ChatSession session(Long id, Long userId, String status, LocalDateTime endedAt) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setSubject("math");
        session.setStatus(status);
        session.setEndedAt(endedAt);
        return session;
    }
}
