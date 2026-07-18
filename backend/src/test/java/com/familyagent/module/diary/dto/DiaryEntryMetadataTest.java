package com.familyagent.module.diary.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiaryEntryMetadataTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeTypedFieldsAndPreserveExtras() throws Exception {
        DiaryEntryMetadata metadata = objectMapper.readValue("""
                {
                  "source": " FAMILY_COMPANION_TOOL ",
                  "relatedUserId": 42,
                  "eventAt": "2026-06-08T09:30:00Z",
                  "disableAutoMerge": true,
                  "plannedTool": "DIARY"
                }
                """, DiaryEntryMetadata.class);

        assertEquals(" FAMILY_COMPANION_TOOL ", metadata.getSource());
        assertEquals(42L, metadata.getRelatedUserId());
        assertEquals("2026-06-08T09:30:00Z", metadata.getEventAt());
        assertTrue(metadata.getDisableAutoMerge());
        assertEquals("DIARY", metadata.extraProperties().get("plannedTool"));

        Map<String, Object> persisted = metadata.toMap();
        assertEquals("FAMILY_COMPANION_TOOL", persisted.get("source"));
        assertEquals(42L, persisted.get("relatedUserId"));
        assertEquals("DIARY", persisted.get("plannedTool"));

        JsonNode serialized = objectMapper.readTree(objectMapper.writeValueAsString(metadata));
        assertEquals("DIARY", serialized.get("plannedTool").textValue());
        assertFalse(serialized.has("extra"));
    }
}
