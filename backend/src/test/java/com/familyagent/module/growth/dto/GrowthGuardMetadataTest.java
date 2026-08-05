package com.familyagent.module.growth.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GrowthGuardMetadataTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeTypedFieldsAndPreserveExtras() throws Exception {
        GrowthGuardMetadata metadata = objectMapper.readValue("""
                {
                  "source": " MIRROR_AGENT_TOOL ",
                  "relatedUserId": 42,
                  "sourceType": "GROWTH_OBSERVATION",
                  "followUpStatus": "PENDING",
                  "memoryLibrary": "FAMILY",
                  "memoryType": "OBSERVATION"
                }
                """, GrowthGuardMetadata.class);

        assertEquals(" MIRROR_AGENT_TOOL ", metadata.getSource());
        assertEquals(42L, metadata.getRelatedUserId());
        assertEquals("PENDING", metadata.getFollowUpStatus());
        assertEquals("FAMILY", metadata.extraProperties().get("memoryLibrary"));
        assertEquals("OBSERVATION", metadata.extraProperties().get("memoryType"));

        Map<String, Object> persisted = metadata.toMap();
        assertEquals("MIRROR_AGENT_TOOL", persisted.get("source"));
        assertEquals("FAMILY", persisted.get("memoryLibrary"));
        assertEquals("OBSERVATION", persisted.get("memoryType"));

        JsonNode serialized = objectMapper.readTree(objectMapper.writeValueAsString(metadata));
        assertEquals("FAMILY", serialized.get("memoryLibrary").textValue());
        assertEquals("OBSERVATION", serialized.get("memoryType").textValue());
        assertFalse(serialized.has("extra"));
    }
}
