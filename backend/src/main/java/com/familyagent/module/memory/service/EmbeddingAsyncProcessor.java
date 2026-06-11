package com.familyagent.module.memory.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Spring-managed async processor for background embedding tasks.
 * <p>
 * Replaces bare {@code CompletableFuture.runAsync()} so that Spring
 * manages the thread pool lifecycle and shutdown ordering.
 */
@Slf4j
@Component
public class EmbeddingAsyncProcessor {

    @Async("embeddingTaskExecutor")
    public void execute(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("Embedding async task failed: {}", e.getMessage());
        }
    }
}
