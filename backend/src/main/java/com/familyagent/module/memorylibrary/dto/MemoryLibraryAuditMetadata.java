package com.familyagent.module.memorylibrary.dto;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryLibraryMetadataSource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public record MemoryLibraryAuditMetadata(
        Long actorUserId,
        LocalDateTime occurredAt,
        MemoryLibraryMetadataSource source) {

    public Map<String, Object> mergeEdit(Object existingMetadata) {
        Map<String, Object> metadata = mutableMap(existingMetadata);
        metadata.put("lastEditedBy", actorUserId);
        metadata.put("lastEditedAt", occurredAt.toString());
        metadata.put("editSource", source.name());
        return metadata;
    }

    public Map<String, Object> mergeArchive(Object existingMetadata, EntityStatus metadataStatus) {
        Map<String, Object> metadata = mutableMap(existingMetadata);
        putStatus(metadata, metadataStatus);
        metadata.put("archivedBy", actorUserId);
        metadata.put("archivedAt", occurredAt.toString());
        metadata.put("archiveSource", source.name());
        return metadata;
    }

    public Map<String, Object> mergeRestore(Object existingMetadata, EntityStatus metadataStatus) {
        Map<String, Object> metadata = mutableMap(existingMetadata);
        putStatus(metadata, metadataStatus);
        metadata.put("restoredBy", actorUserId);
        metadata.put("restoredAt", occurredAt.toString());
        metadata.put("restoreSource", source.name());
        return metadata;
    }

    private static void putStatus(Map<String, Object> metadata, EntityStatus status) {
        if (status != null) {
            metadata.put("status", status.name());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMap(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }
}
