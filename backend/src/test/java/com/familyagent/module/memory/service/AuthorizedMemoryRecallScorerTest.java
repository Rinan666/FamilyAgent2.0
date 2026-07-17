package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizedMemoryRecallScorerTest {

    private final AuthorizedMemoryRecallScorer scorer = new AuthorizedMemoryRecallScorer();

    @Test
    void supports_shouldAcceptDirectTextEvidence() {
        DiaryEntry diary = new DiaryEntry();
        diary.setRawText("Grandma shared a bedtime routine");
        diary.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));

        assertTrue(scorer.supports(diary, "bedtime routine"));
        assertTrue(scorer.score(diary, "bedtime routine") > 0);
    }

    @Test
    void supports_shouldAcceptStableIndexEvidence() {
        MemoryEntry memory = new MemoryEntry();
        memory.setContent("A general family note");
        memory.setSummary("Daily routine");
        memory.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        memory.setMetadata(Map.of(
                "index", Map.of(
                        "topics", List.of("HEALTH"),
                        "scenes", List.of(),
                        "temporalLayer", "STABLE")));

        assertTrue(scorer.supports(memory, "health"));
        assertTrue(scorer.score(memory, "health") > 0);
    }

    @Test
    void supports_shouldRejectBlankOrUnrelatedQuery() {
        DiaryEntry diary = new DiaryEntry();
        diary.setRawText("Family picnic notes");

        assertFalse(scorer.supports(diary, ""));
        assertFalse(scorer.supports(diary, "bedtime routine"));
    }
}
