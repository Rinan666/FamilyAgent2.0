package com.familyagent.module.mirror.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MirrorTemporalLayerAnnotatorTest {

    private final MirrorTemporalLayerAnnotator annotator = new MirrorTemporalLayerAnnotator();

    @Test
    void annotatesDiaryMemoryAndGrowthWithTheirDomainSpecificLayers() {
        DiaryEntry diary = new DiaryEntry();
        diary.setCreatedAt(LocalDateTime.now().minusDays(5));

        MemoryEntry memory = new MemoryEntry();
        memory.setType("VALUE");
        memory.setImportance(4);
        memory.setCreatedAt(LocalDateTime.now().minusYears(3));

        GrowthGuardRecord growth = new GrowthGuardRecord();
        growth.setObservedAt(LocalDate.now().minusDays(120));
        growth.setSeverity(3);

        annotator.annotate(List.of(diary), List.of(memory), List.of(growth));

        assertEquals("FRESH", metadata(diary.getMetadata()).get("temporalLayer"));
        assertEquals("CORE_MEMORY", metadata(memory.getMetadata()).get("temporalLayer"));
        assertEquals("IMPRESSION", metadata(growth.getMetadata()).get("temporalLayer"));
    }

    @Test
    void metadataEventTimeOverridesFallbackCreationTime() {
        DiaryEntry diary = new DiaryEntry();
        diary.setCreatedAt(LocalDateTime.now());
        diary.setMetadata(Map.of("eventAt", LocalDate.now().minusYears(1).toString()));

        annotator.annotate(List.of(diary), List.of(), List.of());

        assertEquals("IMPRESSION", metadata(diary.getMetadata()).get("temporalLayer"));
    }

    private static Map<?, ?> metadata(Object value) {
        return (Map<?, ?>) value;
    }
}
