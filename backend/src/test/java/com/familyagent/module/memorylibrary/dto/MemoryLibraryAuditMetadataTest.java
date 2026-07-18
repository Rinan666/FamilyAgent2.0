package com.familyagent.module.memorylibrary.dto;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryLibraryMetadataSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MemoryLibraryAuditMetadataTest {

    private final LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 18, 16, 15);

    @Test
    void mergeEdit_shouldPreserveUnknownFieldsAndExistingStatus() {
        MemoryLibraryAuditMetadata audit = new MemoryLibraryAuditMetadata(
                101L,
                occurredAt,
                MemoryLibraryMetadataSource.MEMORY_LIBRARY_MAINTENANCE);

        Map<String, Object> result = audit.mergeEdit(Map.of("status", "ACTIVE", "custom", true));

        assertEquals("ACTIVE", result.get("status"));
        assertEquals(true, result.get("custom"));
        assertEquals(101L, result.get("lastEditedBy"));
        assertEquals("2026-07-18T16:15", result.get("lastEditedAt"));
        assertEquals("MEMORY_LIBRARY_MAINTENANCE", result.get("editSource"));
    }

    @Test
    void archiveAndRestore_shouldOnlyWriteStatusWhenRequested() {
        MemoryLibraryAuditMetadata archiveAudit = new MemoryLibraryAuditMetadata(
                101L,
                occurredAt,
                MemoryLibraryMetadataSource.MEMORY_LIBRARY_MAINTENANCE);
        MemoryLibraryAuditMetadata restoreAudit = new MemoryLibraryAuditMetadata(
                102L,
                occurredAt,
                MemoryLibraryMetadataSource.MEMORY_LIBRARY_ARCHIVE_BOX);

        Map<String, Object> diaryArchive = archiveAudit.mergeArchive(Map.of(), EntityStatus.ARCHIVED);
        Map<String, Object> memoryArchive = archiveAudit.mergeArchive(Map.of(), null);
        Map<String, Object> diaryRestore = restoreAudit.mergeRestore(diaryArchive, EntityStatus.ACTIVE);

        assertEquals("ARCHIVED", diaryArchive.get("status"));
        assertFalse(memoryArchive.containsKey("status"));
        assertEquals("ACTIVE", diaryRestore.get("status"));
        assertEquals(102L, diaryRestore.get("restoredBy"));
        assertEquals("MEMORY_LIBRARY_ARCHIVE_BOX", diaryRestore.get("restoreSource"));
    }
}
