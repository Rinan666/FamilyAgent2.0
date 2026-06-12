package com.familyagent.module.session.dto;

public record ChatSessionArchiveMetadata(
        Long lastArchiveId,
        String lastArchiveAt,
        String lastArchiveRange,
        int storageVersion
) {
}
