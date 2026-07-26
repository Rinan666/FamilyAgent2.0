package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.family.facade.FamilyRelationshipGraphView;
import com.familyagent.module.family.facade.FamilyRelationshipNode;
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
        diary.setUserId(101L);
        diary.setStructured(Map.of("title", "Family walk"));
        diary.setRawText("We took a walk together after dinner.");
        diary.setVisibility("FAMILY_VISIBLE");
        diary.setMetadata(metadata("STABLE", Arrays.asList("HEALTH", null, "BONDING"), List.of("HOME")));

        MemoryEntry memory = new MemoryEntry();
        memory.setId(2L);
        memory.setUserId(202L);
        memory.setSummary("Grandma's advice");
        memory.setContent("Listen before answering.");
        memory.setType("ELDER_ADVICE");
        memory.setScope("FAMILY_VISIBLE");
        memory.setMetadata(metadata("LONG_TERM", List.of("VALUES"), List.of("FAMILY")));

        GrowthGuardRecord growth = new GrowthGuardRecord();
        growth.setId(3L);
        growth.setCreatedBy(101L);
        growth.setTargetUserId(303L);
        growth.setCategory("LEARNING");
        growth.setContent("The child now asks for clarification before starting homework.");
        growth.setVisibility("CARE_VISIBLE");
        growth.setMetadata(metadata("RECENT", List.of("LEARNING"), List.of("HOMEWORK")));

        List<RecallSourceSummary> result = assembler.assemble(
                Arrays.asList(null, diary),
                List.of(memory),
                List.of(growth),
                relationships());

        assertEquals(List.of("diary-1", "memory-2", "growth-3"),
                result.stream().map(RecallSourceSummary::getId).toList());
        assertEquals("Family walk", result.get(0).getTitle());
        assertEquals(List.of("HEALTH", "BONDING"), result.get(0).getTopics());
        assertEquals("Grandma's advice", result.get(1).getTitle());
        assertEquals("LEARNING", result.get(2).getTitle());
        assertEquals("current viewer", result.get(0).getAuthor().name());
        assertEquals("二叔", result.get(1).getAuthor().relationshipToViewer());
        assertEquals("孩子", result.get(2).getSubject().relationshipToViewer());
        assertTrue(result.stream().allMatch(item -> item.getSnippet().length() <= 93));
        assertFalse(result.toString().contains("private metadata detail"));
    }

    private static FamilyRelationshipGraphView relationships() {
        return new FamilyRelationshipGraphView(Map.of(
                101L, new FamilyRelationshipNode(101L, "current viewer", "本人", null, true, false),
                202L, new FamilyRelationshipNode(202L, "Uncle Zhang", "二叔", null, false, false),
                303L, new FamilyRelationshipNode(303L, "Child", "孩子", null, false, false)));
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
