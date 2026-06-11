package com.familyagent.module.session.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs legacy session backfill asynchronously after the application context is ready.
 * <p>
 * Using {@link ApplicationRunner} + {@code @Async} avoids blocking bean initialization
 * ({@code @PostConstruct}) or application startup — the container is healthy while
 * the potentially long-running backfill executes in the background.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionBackfillInitializer implements ApplicationRunner {

    private final ChatSessionService chatSessionService;

    @Override
    @Async
    public void run(ApplicationArguments args) {
        try {
            chatSessionService.backfillLegacySessions();
        } catch (Exception error) {
            log.warn("Chat session backfill initializer failed: {}", error.getMessage());
        }
    }
}
