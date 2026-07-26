package com.familyagent.module.memory.facade;

import java.time.LocalDateTime;

public record UnifiedMemoryCreateResult(
        Long memoryEntryId,
        Long originId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
