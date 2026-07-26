package com.familyagent.module.memory.service;

import com.familyagent.module.memory.entity.MemoryEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedMemoryIndexMetadataAssemblerTest {

    private final UnifiedMemoryIndexMetadataAssembler assembler = new UnifiedMemoryIndexMetadataAssembler();

    @Test
    void enrichesDiaryAndGrowthFromTypedLegacyMetadata() {
        MemoryEntry diary = entry(1L, "DIARY");
        diary.setMetadata(Map.of("legacyDiary", Map.of("entryType", "LESSON", "mood", "CALM")));
        MemoryEntry growth = entry(2L, "GROWTH");
        growth.setMetadata(Map.of("legacyGrowth", Map.of("category", "VISION", "severity", 4)));

        Map<String, Object> diaryMetadata = assembler.enrich(diary);
        Map<String, Object> growthMetadata = assembler.enrich(growth);

        assertTrue(diaryMetadata.containsKey("index"));
        assertTrue(growthMetadata.containsKey("index"));
        assertEquals("VISION", ((Map<?, ?>) growthMetadata.get("legacyGrowth")).get("category"));
    }

    private static MemoryEntry entry(Long id, String originType) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setOriginType(originType);
        entry.setType("OBSERVATION");
        entry.setContent("Family content");
        entry.setImportance(3);
        entry.setOccurredAt(LocalDateTime.of(2026, 7, 26, 10, 0));
        entry.setTags(new String[] {"family"});
        return entry;
    }
}
