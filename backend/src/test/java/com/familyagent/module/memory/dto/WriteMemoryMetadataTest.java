package com.familyagent.module.memory.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
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
                  "sourceDiaryId": 42,
                  "authorName": "Taylor",
                  "relatedMemberName": "Alex",
                  "observerPerspective": "FAMILY_MEMBER",
                  "evidenceType": "OBSERVED_FACT",
                  "legacyFlag": true,
                  "legacyNullable": null
                }
                """, WriteMemoryMetadata.class);

        assertEquals(" WRITE_MEMORY_SIMPLIFIED ", metadata.getSource());
        assertEquals(42L, metadata.getSourceDiaryId());
        assertEquals("Taylor", metadata.getAuthorName());
        assertEquals(true, metadata.extraProperties().get("legacyFlag"));
        assertTrue(metadata.extraProperties().containsKey("legacyNullable"));
        assertNull(metadata.extraProperties().get("legacyNullable"));

        Map<String, Object> persisted = metadata.toMap();
        assertEquals("WRITE_MEMORY_SIMPLIFIED", persisted.get("source"));
        assertEquals(42L, persisted.get("sourceDiaryId"));
        assertEquals("Taylor", persisted.get("authorName"));
        assertEquals(true, persisted.get("legacyFlag"));
        assertFalse(persisted.containsKey("extra"));

        JsonNode serialized = objectMapper.readTree(objectMapper.writeValueAsString(metadata));
        assertTrue(serialized.get("legacyFlag").booleanValue());
        assertFalse(serialized.has("extra"));
    }

    @Test
    void shouldConvertMapWithoutLosingTypedOrExtraFields() {
        WriteMemoryMetadata metadata = WriteMemoryMetadata.fromMap(Map.of(
                "source", "DIARY_PROMOTION",
                "sourceDiaryId", "99",
                "plannedTool", "FAMILY_MEMORY"));

        assertEquals("DIARY_PROMOTION", metadata.getSource());
        assertEquals(99L, metadata.getSourceDiaryId());
        assertEquals("FAMILY_MEMORY", metadata.toMap().get("plannedTool"));
    }

    @Test
    void createFamilyMemoryRequestShouldRetainMetadataSizeLimit() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < 51; index++) {
            values.put("extra" + index, index);
        }
        CreateFamilyMemoryRequest request = new CreateFamilyMemoryRequest();
        request.setFamilyId(1L);
        request.setContent("A family memory with enough detail.");
        request.setMetadata(WriteMemoryMetadata.fromMap(values));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(request).isEmpty());
        }
    }
}
