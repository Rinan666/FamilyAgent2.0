package com.familyagent.module.session.service;

import com.familyagent.common.lifecycle.PersonaScopedResourceCleaner;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionPersonaResourceCleaner implements PersonaScopedResourceCleaner {

    private final ChatSessionRepository sessionRepository;
    private final ChatSessionService sessionService;

    @Override
    @Transactional
    public void cleanPersonaResources(Long familyId, Long personaId) {
        List<ChatSession> sessions = sessionRepository.findByFamilyAndTargetPersonaId(familyId, personaId);
        for (ChatSession session : sessions) {
            sessionService.deleteSessionData(session);
        }
        if (!sessions.isEmpty()) {
            log.info("Persona chat sessions cleaned: familyId={}, personaId={}, count={}",
                    familyId, personaId, sessions.size());
        }
    }
}
