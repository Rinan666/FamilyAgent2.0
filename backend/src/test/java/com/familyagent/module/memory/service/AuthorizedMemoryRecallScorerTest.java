package com.familyagent.module.memory.service;

import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
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
    void supportsDirectTextAndStableIndexEvidence() {
        AuthorizedMemoryRecallCandidate text = candidate("Grandma shared a bedtime routine", null);
        AuthorizedMemoryRecallCandidate indexed = candidate("A general family note", Map.of(
                "index", Map.of("topics", List.of("HEALTH"), "scenes", List.of())));

        assertTrue(scorer.supports(text, "bedtime routine"));
        assertTrue(scorer.score(text, "bedtime routine") > 0);
        assertTrue(scorer.supports(indexed, "health"));
        assertFalse(scorer.supports(text, "zxqv"));
        assertFalse(scorer.supports(text, ""));
    }

    private static AuthorizedMemoryRecallCandidate candidate(String content, Object metadata) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(1L);
        entry.setLibraryKind("FAMILY");
        entry.setContent(content);
        entry.setMetadata(metadata);
        entry.setOccurredAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        return AuthorizedMemoryRecallCandidate.from(entry);
    }
}
