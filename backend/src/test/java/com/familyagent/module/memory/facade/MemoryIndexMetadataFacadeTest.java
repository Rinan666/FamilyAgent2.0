package com.familyagent.module.memory.facade;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryIndexMetadataFacadeTest {

    private final MemoryIndexMetadataFacade facade = new MemoryIndexMetadataFacade();

    @Test
    void shouldPreserveDiaryAndGrowthSourceKinds() {
        Map<String, Object> diary = facade.enrichDiary(
                Map.of(), "diary", "DAILY", null, new String[0]);
        Map<String, Object> growth = facade.enrichGrowth(
                Map.of(), "growth", "SLEEP", 3, LocalDate.of(2026, 8, 2));

        assertEquals("DIARY", sourceKind(diary));
        assertEquals("GROWTH_OBSERVATION", sourceKind(growth));
    }

    private static Object sourceKind(Map<String, Object> metadata) {
        return ((Map<?, ?>) metadata.get("index")).get("sourceKind");
    }
}
