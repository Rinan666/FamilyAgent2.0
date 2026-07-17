package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.repository.MemoryRecallVectorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizedMemoryRecallRankingServiceTest {

    @Mock private AuthorizedMemoryRecallEmbeddingService embeddingService;
    @Mock private MemoryRecallVectorRepository vectorRepository;

    @Test
    void rank_propagatesEmbeddingObservationWithoutOwningProviderLogic() {
        EmbeddingCallObservation observation = new EmbeddingCallObservation(
                true, true, false, "local", "local/hash-embedding", 1536, 18L, null);
        when(embeddingService.embed(10L, "bedtime reminder"))
                .thenReturn(new AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding(
                        java.util.Collections.nCopies(1536, 0.01),
                        observation));
        AuthorizedMemoryRecallRankingService service = new AuthorizedMemoryRecallRankingService(
                embeddingService,
                vectorRepository,
                new AuthorizedMemoryRecallTextRanker(new AuthorizedMemoryRecallScorer()));

        AuthorizedMemoryRecallRankingService.RankedRecall result = service.rank(
                10L,
                "bedtime reminder",
                List.of(),
                List.of(),
                List.of(),
                3,
                3,
                1L);

        assertEquals(observation, result.embeddingObservation());
        verifyNoInteractions(vectorRepository);
    }

    @Test
    void rank_shouldDelegateVectorCandidatesToRepository() {
        EmbeddingCallObservation observation = observation();
        List<Double> values = Collections.nCopies(1536, 0.01);
        DiaryEntry diary = diary(44L, "bedtime reminder");
        when(embeddingService.embed(10L, "bedtime reminder"))
                .thenReturn(new AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding(values, observation));
        when(vectorRepository.rankSourceIds(
                10L,
                "DIARY",
                List.of(44L),
                values,
                0.72,
                3))
                .thenReturn(List.of(44L));
        AuthorizedMemoryRecallRankingService service = new AuthorizedMemoryRecallRankingService(
                embeddingService,
                vectorRepository,
                new AuthorizedMemoryRecallTextRanker(new AuthorizedMemoryRecallScorer()));

        AuthorizedMemoryRecallRankingService.RankedRecall result = service.rank(
                10L,
                "bedtime reminder",
                List.of(diary),
                List.of(),
                List.of(),
                3,
                3,
                1L);

        assertTrue(result.usedVector());
        assertEquals(List.of(diary), result.diaries());
        verify(vectorRepository).rankSourceIds(
                eq(10L),
                eq("DIARY"),
                eq(List.of(44L)),
                eq(values),
                eq(0.72),
                eq(3));
    }

    @Test
    void rank_shouldUseTextFallbackWhenVectorRepositoryFails() {
        EmbeddingCallObservation observation = observation();
        List<Double> values = Collections.nCopies(1536, 0.01);
        DiaryEntry diary = diary(44L, "bedtime reminder");
        when(embeddingService.embed(10L, "bedtime reminder"))
                .thenReturn(new AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding(values, observation));
        when(vectorRepository.rankSourceIds(
                10L,
                "DIARY",
                List.of(44L),
                values,
                0.72,
                3))
                .thenThrow(new IllegalStateException("database detail"));
        AuthorizedMemoryRecallRankingService service = new AuthorizedMemoryRecallRankingService(
                embeddingService,
                vectorRepository,
                new AuthorizedMemoryRecallTextRanker(new AuthorizedMemoryRecallScorer()));

        AuthorizedMemoryRecallRankingService.RankedRecall result = service.rank(
                10L,
                "bedtime reminder",
                List.of(diary),
                List.of(),
                List.of(),
                3,
                3,
                1L);

        assertFalse(result.usedVector());
        assertEquals(List.of(diary), result.diaries());
        assertEquals(observation, result.embeddingObservation());
    }

    private static EmbeddingCallObservation observation() {
        return new EmbeddingCallObservation(
                true, true, false, "local", "local/hash-embedding", 1536, 18L, null);
    }

    private static DiaryEntry diary(Long id, String rawText) {
        DiaryEntry entry = new DiaryEntry();
        entry.setId(id);
        entry.setRawText(rawText);
        entry.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        return entry;
    }
}
