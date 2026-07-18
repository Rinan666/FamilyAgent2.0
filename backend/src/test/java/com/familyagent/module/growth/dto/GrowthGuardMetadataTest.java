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
                  "plannedTool": "GROWTH_GUARD"
                }
                """, GrowthGuardMetadata.class);

        assertEquals(" MIRROR_AGENT_TOOL ", metadata.getSource());
        assertEquals(42L, metadata.getRelatedUserId());
        assertEquals("PENDING", metadata.getFollowUpStatus());
        assertEquals("GROWTH_GUARD", metadata.extraProperties().get("plannedTool"));

        Map<String, Object> persisted = metadata.toMap();
        assertEquals("MIRROR_AGENT_TOOL", persisted.get("source"));
        assertEquals("GROWTH_GUARD", persisted.get("plannedTool"));

        JsonNode serialized = objectMapper.readTree(objectMapper.writeValueAsString(metadata));
        assertEquals("GROWTH_GUARD", serialized.get("plannedTool").textValue());
        assertFalse(serialized.has("extra"));
    }
}
