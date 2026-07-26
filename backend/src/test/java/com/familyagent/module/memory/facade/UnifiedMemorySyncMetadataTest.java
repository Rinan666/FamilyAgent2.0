package com.familyagent.module.memory.facade;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedMemorySyncMetadataTest {

    @Test
    void persistenceMapFlattensCompatibilityMetadataWithoutDroppingNullValues() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("diaryDate", "2026-07-27");
        extra.put("optional", null);
        UnifiedMemorySyncMetadata metadata = UnifiedMemorySyncMetadata.diary(
                "DAILY",
                null,
                "DIARY_MANUAL",
                null,
                extra);

        Map<String, Object> persisted = metadata.toPersistenceMap();

        assertEquals("2026-07-27", persisted.get("diaryDate"));
        assertTrue(persisted.containsKey("optional"));
        assertNull(persisted.get("optional"));
        assertEquals(metadata.legacyDiary(), persisted.get("legacyDiary"));
    }
}
