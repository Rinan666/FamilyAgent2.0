package com.familyagent.module.memorylibrary.dto;

import com.familyagent.common.constant.MemoryLibraryMetadataSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryClassicalizationMetadataTest {

    @Test
    void mergeInto_shouldPreserveOriginalAndUnknownFields() {
        LocalDateTime classicalizedAt = LocalDateTime.of(2026, 7, 18, 15, 30);
        MemoryClassicalizationMetadata metadata = new MemoryClassicalizationMetadata(
                "new original",
                "new summary",
                "plain summary",
                "style note",
                classicalizedAt,
                101L,
                MemoryLibraryMetadataSource.MEMORY_LIBRARY_CLASSICALIZE);

        Map<String, Object> result = metadata.mergeInto(Map.of(
                "originalContent", "preserved original",
                "customField", true));

        assertEquals("preserved original", result.get("originalContent"));
        assertEquals("new summary", result.get("originalSummary"));
        assertEquals("plain summary", result.get("plainSummary"));
        assertEquals("style note", result.get("styleNote"));
        assertEquals("2026-07-18T15:30", result.get("classicalizedAt"));
        assertEquals(101L, result.get("classicalizedBy"));
        assertEquals("MEMORY_LIBRARY_CLASSICALIZE", result.get("source"));
        assertEquals(true, result.get("customField"));
    }
}
