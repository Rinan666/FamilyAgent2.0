package com.familyagent.module.session.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

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

    @Test
    void endSession_shouldTransitionActiveSessionWithoutLegacyMemoryExtraction() {
        ChatSession active = session(100L, 10L, "ACTIVE", null);
        ChatSession ended = session(100L, 10L, "ENDED", LocalDateTime.now());

        when(sessionRepository.findStatusById(100L)).thenReturn(active, ended);
        when(sessionRepository.endActiveSession(eq(100L), eq("done"), any(LocalDateTime.class))).thenReturn(1);

        ChatSessionService service = new ChatSessionService(sessionRepository, familyService);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            ChatSession result = service.endSession(100L, "done", "Bearer token");

            assertEquals("ENDED", result.getStatus());
            assertEquals("done", result.getSummary());
            assertNotNull(result.getEndedAt());
        }

        verify(sessionRepository, never()).selectById(100L);
    }

    @Test
    void endSession_shouldBeIdempotentWhenSessionAlreadyEnded() {
        LocalDateTime endedAt = LocalDateTime.now().minusMinutes(5);
        ChatSession ended = session(100L, 10L, "ENDED", endedAt);

        when(sessionRepository.findStatusById(100L)).thenReturn(ended, ended);
        when(sessionRepository.endActiveSession(eq(100L), eq(null), any(LocalDateTime.class))).thenReturn(0);

        ChatSessionService service = new ChatSessionService(sessionRepository, familyService);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            ChatSession result = service.endSession(100L, null, "Bearer token");

            assertEquals("ENDED", result.getStatus());
            assertEquals(endedAt, result.getEndedAt());
        }

        verify(sessionRepository, never()).fillMissingEndedAt(any(), any());
        verify(sessionRepository, never()).selectById(100L);
    }

    @Test
    void endSession_shouldRepairEndedSessionWithoutEndedAt() {
        ChatSession endedWithoutTime = session(100L, 10L, "ENDED", null);

        when(sessionRepository.findStatusById(100L)).thenReturn(endedWithoutTime, endedWithoutTime);
        when(sessionRepository.endActiveSession(eq(100L), eq(null), any(LocalDateTime.class))).thenReturn(0);

        ChatSessionService service = new ChatSessionService(sessionRepository, familyService);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            ChatSession result = service.endSession(100L, " ", null);

            assertEquals("ENDED", result.getStatus());
            assertNotNull(result.getEndedAt());
        }

        verify(sessionRepository).fillMissingEndedAt(eq(100L), any(LocalDateTime.class));
        verify(sessionRepository, never()).selectById(100L);
    }

    @Test
    void updateMessages_shouldUpdateOnlyMessages() {
        ChatSession existing = session(100L, 10L, "ENDED", LocalDateTime.now());
        List<Object> messages = List.of();

        when(sessionRepository.selectById(100L)).thenReturn(existing);

        ChatSessionService service = new ChatSessionService(sessionRepository, familyService);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            ChatSession result = service.updateMessages(100L, messages);

            assertEquals(messages, result.getMessages());
            assertEquals("ENDED", result.getStatus());
        }

        verify(sessionRepository).updateSessionMessages(100L, messages);
        verify(sessionRepository, never()).updateById(any(ChatSession.class));
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
