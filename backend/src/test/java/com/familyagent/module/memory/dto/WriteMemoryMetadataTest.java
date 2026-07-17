package com.familyagent.module.memory.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteMemoryMetadataTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeTypedFieldsAndPreserveLegacyExtras() throws Exception {
        WriteMemoryMetadata metadata = objectMapper.readValue("""
                {
                  "source": " WRITE_MEMORY_SIMPLIFIED ",
                  "authorName": "Taylor",
                  "relatedMemberName": "Alex",
                  "observerPerspective": "FAMILY_MEMBER",
                  "evidenceType": "OBSERVED_FACT",
                  "legacyFlag": true,
                  "legacyNullable": null
                }
                """, WriteMemoryMetadata.class);

        assertEquals(" WRITE_MEMORY_SIMPLIFIED ", metadata.getSource());
        assertEquals("Taylor", metadata.getAuthorName());
        assertEquals(true, metadata.extraProperties().get("legacyFlag"));
        assertTrue(metadata.extraProperties().containsKey("legacyNullable"));
        assertNull(metadata.extraProperties().get("legacyNullable"));

        Map<String, Object> persisted = metadata.toMap();
        assertEquals("WRITE_MEMORY_SIMPLIFIED", persisted.get("source"));
        assertEquals("Taylor", persisted.get("authorName"));
        assertEquals(true, persisted.get("legacyFlag"));
        assertFalse(persisted.containsKey("extra"));

        JsonNode serialized = objectMapper.readTree(objectMapper.writeValueAsString(metadata));
        assertTrue(serialized.get("legacyFlag").booleanValue());
        assertFalse(serialized.has("extra"));
    }
}
