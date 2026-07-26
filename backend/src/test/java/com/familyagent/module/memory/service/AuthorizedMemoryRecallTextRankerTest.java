package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizedMemoryRecallTextRankerTest {

    private final AuthorizedMemoryRecallTextRanker ranker =
            new AuthorizedMemoryRecallTextRanker(new AuthorizedMemoryRecallScorer());

    @Test
    void rankRejectsUnsupportedVectorCandidate() {
        AuthorizedMemoryRecallCandidate unrelated = diary(101L, 1L, "Family picnic notes");

        AuthorizedMemoryRecallTextRanker.RankedCandidates ranked = ranker.rank(
                List.of(unrelated), List.of(), List.of(), List.of(101L), List.of(), List.of(),
                "bedtime routine", 3, 3);

        assertFalse(ranked.usedVector());
        assertEquals(List.of(), ranked.diaries());
    }

    @Test
    void rankKeepsSupportedVectorCandidateAndTextFallback() {
        AuthorizedMemoryRecallCandidate supported = diary(102L, 2L, "Grandma shared a bedtime routine");
        AuthorizedMemoryRecallCandidate fallback = diary(103L, 3L, "A shorter bedtime note");

        AuthorizedMemoryRecallTextRanker.RankedCandidates ranked = ranker.rank(
                List.of(fallback, supported), List.of(), List.of(), List.of(102L), List.of(), List.of(),
                "bedtime routine", 3, 3);

        assertTrue(ranked.usedVector());
        assertEquals(List.of(2L, 3L),
                ranked.diaries().stream().map(AuthorizedMemoryRecallCandidate::publicSourceId).toList());
    }

    private static AuthorizedMemoryRecallCandidate diary(Long id, Long originId, String content) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setLibraryKind("FAMILY");
        entry.setOriginType(MemoryOriginType.DIARY.name());
        entry.setOriginId(originId);
        entry.setContent(content);
        entry.setOccurredAt(LocalDateTime.of(2026, 7, originId.intValue(), 10, 0));
        return AuthorizedMemoryRecallCandidate.from(entry);
    }
}
