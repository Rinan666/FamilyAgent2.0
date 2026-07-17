package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.dto.RecallSourceSummary;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizedMemoryRecallSourceAssemblerTest {

    private final AuthorizedMemoryRecallSourceAssembler assembler =
            new AuthorizedMemoryRecallSourceAssembler();

    @Test
    void assemble_shouldMapStableSourceFieldsWithoutLeakingUnrelatedMetadata() {
        DiaryEntry diary = new DiaryEntry();
        diary.setId(1L);
        diary.setStructured(Map.of("title", "Family walk"));
        diary.setRawText("We took a walk together after dinner.");
        diary.setVisibility("FAMILY_VISIBLE");
        diary.setMetadata(metadata("STABLE", Arrays.asList("HEALTH", null, "BONDING"), List.of("HOME")));

        MemoryEntry memory = new MemoryEntry();
        memory.setId(2L);
        memory.setSummary("Grandma's advice");
        memory.setContent("Listen before answering.");
        memory.setType("ELDER_ADVICE");
        memory.setScope("FAMILY_VISIBLE");
        memory.setMetadata(metadata("LONG_TERM", List.of("VALUES"), List.of("FAMILY")));

        GrowthGuardRecord growth = new GrowthGuardRecord();
        growth.setId(3L);
        growth.setCategory("LEARNING");
        growth.setContent("The child now asks for clarification before starting homework.");
        growth.setVisibility("CARE_VISIBLE");
        growth.setMetadata(metadata("RECENT", List.of("LEARNING"), List.of("HOMEWORK")));

        List<RecallSourceSummary> result = assembler.assemble(
                Arrays.asList(null, diary),
                List.of(memory),
                List.of(growth));

        assertEquals(List.of("diary-1", "memory-2", "growth-3"),
                result.stream().map(RecallSourceSummary::getId).toList());
        assertEquals("Family walk", result.get(0).getTitle());
        assertEquals(List.of("HEALTH", "BONDING"), result.get(0).getTopics());
        assertEquals("Grandma's advice", result.get(1).getTitle());
        assertEquals("LEARNING", result.get(2).getTitle());
        assertTrue(result.stream().allMatch(item -> item.getSnippet().length() <= 93));
        assertFalse(result.toString().contains("private metadata detail"));
    }

    private static Map<String, Object> metadata(
            String temporalLayer,
            List<String> topics,
            List<String> scenes) {
        return Map.of(
                "privateNote", "private metadata detail",
                "index", Map.of(
                        "temporalLayer", temporalLayer,
                        "topics", topics,
                        "scenes", scenes));
    }
}
