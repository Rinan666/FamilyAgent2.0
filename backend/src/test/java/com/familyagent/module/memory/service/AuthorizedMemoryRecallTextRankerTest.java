package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizedMemoryRecallTextRankerTest {

    private final AuthorizedMemoryRecallTextRanker ranker = new AuthorizedMemoryRecallTextRanker();

    @Test
    void rank_shouldRejectVectorOnlyCandidateWithoutTextOrIndexSupport() {
        DiaryEntry unrelated = diary(1L, "Family picnic notes");

        AuthorizedMemoryRecallTextRanker.RankedCandidates ranked = ranker.rank(
                List.of(unrelated),
                List.of(),
                List.of(),
                List.of(1L),
                List.of(),
                List.of(),
                "bedtime routine",
                3,
                3);

        assertFalse(ranked.usedVector());
        assertEquals(List.of(), ranked.diaries());
    }

    @Test
    void rank_shouldKeepSupportedVectorCandidateAndTextFallback() {
        DiaryEntry supported = diary(2L, "Grandma shared a bedtime routine");
        DiaryEntry fallback = diary(3L, "A shorter bedtime note");

        AuthorizedMemoryRecallTextRanker.RankedCandidates ranked = ranker.rank(
                List.of(fallback, supported),
                List.of(),
                List.of(),
                List.of(2L),
                List.of(),
                List.of(),
                "bedtime routine",
                3,
                3);

        assertTrue(ranked.usedVector());
        assertEquals(List.of(2L, 3L), ranked.diaries().stream().map(DiaryEntry::getId).toList());
    }

    private static DiaryEntry diary(Long id, String rawText) {
        DiaryEntry entry = new DiaryEntry();
        entry.setId(id);
        entry.setRawText(rawText);
        entry.setCreatedAt(LocalDateTime.of(2026, 7, id.intValue(), 10, 0));
        return entry;
    }
}
