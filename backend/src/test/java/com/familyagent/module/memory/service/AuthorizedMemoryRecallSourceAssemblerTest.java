package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.family.facade.FamilyRelationshipGraphView;
import com.familyagent.module.family.facade.FamilyRelationshipNode;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
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

    private final AuthorizedMemoryRecallSourceAssembler assembler = new AuthorizedMemoryRecallSourceAssembler();

    @Test
    void assembleMapsUnifiedEntriesToStablePublicSources() {
        AuthorizedMemoryRecallCandidate diary = candidate(
                entry(101L, 1L, MemoryOriginType.DIARY, 101L, null, "Family walk"),
                metadata("STABLE", Arrays.asList("HEALTH", null, "BONDING"), List.of("HOME"),
                        "legacyDiary", Map.of("entryType", "DAILY")));
        AuthorizedMemoryRecallCandidate memory = candidate(
                entry(2L, null, null, 202L, null, "Grandma's advice"),
                metadata("LONG_TERM", List.of("VALUES"), List.of("FAMILY"), null, null));
        AuthorizedMemoryRecallCandidate growth = candidate(
                entry(103L, 3L, MemoryOriginType.GROWTH, 101L, 303L,
                        "The child now asks for clarification."),
                metadata("RECENT", List.of("LEARNING"), List.of("HOMEWORK"),
                        "legacyGrowth", Map.of("category", "LEARNING")));

        List<RecallSourceSummary> result = assembler.assemble(
                List.of(diary), List.of(memory), List.of(growth), relationships());

        assertEquals(List.of("diary-1", "memory-2", "growth-3"),
                result.stream().map(RecallSourceSummary::getId).toList());
        assertEquals("Family walk", result.get(0).getTitle());
        assertEquals(List.of("HEALTH", "BONDING"), result.get(0).getTopics());
        assertEquals("Grandma's advice", result.get(1).getTitle());
        assertEquals("LEARNING", result.get(2).getTitle());
        assertEquals("二叔", result.get(1).getAuthor().relationshipToViewer());
        assertEquals("孩子", result.get(2).getSubject().relationshipToViewer());
        assertTrue(result.stream().allMatch(item -> item.getSnippet().length() <= 93));
        assertFalse(result.toString().contains("private metadata detail"));
    }

    @Test
    void assembleMarksPersonalMemorySeparately() {
        MemoryEntry entry = entry(9L, null, null, 101L, null, "A personal note");
        entry.setLibraryKind("PERSONAL");

        List<RecallSourceSummary> result = assembler.assemble(
                List.of(), List.of(AuthorizedMemoryRecallCandidate.from(entry)), List.of(), relationships());

        assertEquals("PERSONAL_MEMORY", result.get(0).getSourceType());
    }

    private static AuthorizedMemoryRecallCandidate candidate(MemoryEntry entry, Map<String, Object> metadata) {
        entry.setMetadata(metadata);
        return AuthorizedMemoryRecallCandidate.from(entry);
    }

    private static MemoryEntry entry(
            Long id,
            Long originId,
            MemoryOriginType originType,
            Long authorUserId,
            Long relatedUserId,
            String content) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setLibraryKind("FAMILY");
        entry.setOriginId(originId);
        entry.setOriginType(originType == null ? null : originType.name());
        entry.setUserId(authorUserId);
        entry.setRelatedUserId(relatedUserId);
        entry.setTitle(content);
        entry.setContent(content);
        entry.setScope("FAMILY_VISIBLE");
        return entry;
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
            List<String> scenes,
            String legacyKey,
            Object legacyValue) {
        java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("privateNote", "private metadata detail");
        values.put("index", Map.of(
                "temporalLayer", temporalLayer,
                "topics", topics,
                "scenes", scenes));
        if (legacyKey != null) {
            values.put(legacyKey, legacyValue);
        }
        return values;
    }
}
