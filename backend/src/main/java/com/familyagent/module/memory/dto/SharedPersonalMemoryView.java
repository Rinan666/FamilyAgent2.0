package com.familyagent.module.memory.dto;

import com.familyagent.module.memory.entity.MemoryEntry;

import java.time.LocalDateTime;

public record SharedPersonalMemoryView(
        Long id,
        Long ownerUserId,
        String ownerName,
        String relationshipToViewer,
        String type,
        String visibility,
        String content,
        String summary,
        Integer importance,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static SharedPersonalMemoryView from(
            MemoryEntry entry,
            String ownerName,
            String relationshipToViewer) {
        return new SharedPersonalMemoryView(
                entry.getId(),
                entry.getUserId(),
                ownerName,
                relationshipToViewer,
                entry.getType(),
                entry.getScope(),
                entry.getContent(),
                entry.getSummary(),
                entry.getImportance(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
