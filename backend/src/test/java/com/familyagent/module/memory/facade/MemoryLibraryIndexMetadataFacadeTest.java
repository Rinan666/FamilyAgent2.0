package com.familyagent.module.memory.facade;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryLibraryIndexMetadataFacadeTest {

    private final MemoryLibraryIndexMetadataFacade facade = new MemoryLibraryIndexMetadataFacade();

    @Test
    void shouldPreserveSourceKindsForMemoryLibraryEntries() {
        Map<String, Object> diary = facade.enrichDiary(
                Map.of(), "diary", "DAILY", null, new String[0]);
        Map<String, Object> memory = facade.enrichMemory(
                Map.of(), "memory", "summary", "VALUE", 4);
        Map<String, Object> growth = facade.enrichGrowth(
                Map.of(), "growth", "SLEEP", 3, LocalDate.of(2026, 7, 18));

        assertEquals("DIARY", sourceKind(diary));
        assertEquals("FAMILY_MEMORY", sourceKind(memory));
        assertEquals("GROWTH_OBSERVATION", sourceKind(growth));
    }

    private static Object sourceKind(Map<String, Object> metadata) {
        return ((Map<?, ?>) metadata.get("index")).get("sourceKind");
    }
}
