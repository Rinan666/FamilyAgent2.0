package com.familyagent.module.session.service;

import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionPersonaResourceCleanerTest {

    @Mock private ChatSessionRepository sessionRepository;
    @Mock private ChatSessionService sessionService;

    @Test
    void cleanPersonaResources_deletesAllSessionsTargetingPersona() {
        ChatSession first = session(100L);
        ChatSession second = session(101L);
        when(sessionRepository.findByFamilyAndTargetPersonaId(10L, 5L)).thenReturn(List.of(first, second));

        new ChatSessionPersonaResourceCleaner(sessionRepository, sessionService)
                .cleanPersonaResources(10L, 5L);

        InOrder inOrder = inOrder(sessionService);
        inOrder.verify(sessionService).deleteSessionData(first);
        inOrder.verify(sessionService).deleteSessionData(second);
    }

    @Test
    void cleanPersonaResources_doesNothingWhenNoSessionsMatch() {
        when(sessionRepository.findByFamilyAndTargetPersonaId(10L, 5L)).thenReturn(List.of());

        new ChatSessionPersonaResourceCleaner(sessionRepository, sessionService)
                .cleanPersonaResources(10L, 5L);

        verifyNoInteractions(sessionService);
    }

    private static ChatSession session(Long id) {
        ChatSession session = new ChatSession();
        session.setId(id);
        return session;
    }
}
