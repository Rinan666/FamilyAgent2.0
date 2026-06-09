package com.familyagent.module.session.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionBackfillInitializer {

    private final ChatSessionService chatSessionService;

    @PostConstruct
    public void initialize() {
        try {
            chatSessionService.backfillLegacySessions();
        } catch (Exception error) {
            log.warn("Chat session backfill initializer failed: {}", error.getMessage());
        }
    }
}
