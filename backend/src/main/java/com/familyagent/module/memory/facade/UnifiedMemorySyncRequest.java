package com.familyagent.module.memory.facade;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.constant.MemoryScope;

import java.time.LocalDateTime;
import java.util.List;

public record UnifiedMemorySyncRequest(
        Long ownerUserId,
        Long familyId,
        Long relatedUserId,
        MemoryContentType type,
        MemoryScope visibility,
        String title,
        String content,
        List<String> tags,
        LocalDateTime occurredAt,
        MemoryOriginType originType,
        Long originId,
        EntityStatus status) {

    public UnifiedMemorySyncRequest {
        tags = tags == null
                ? List.of()
                : tags.stream()
                        .filter(tag -> tag != null && !tag.isBlank())
                        .map(String::trim)
                        .distinct()
                        .limit(10)
                        .toList();
    }

    public UnifiedMemorySyncRequest withRelatedUserId(Long normalizedRelatedUserId) {
        return new UnifiedMemorySyncRequest(
                ownerUserId,
                familyId,
                normalizedRelatedUserId,
                type,
                visibility,
                title,
                content,
                tags,
                occurredAt,
                originType,
                originId,
                status);
    }
}
