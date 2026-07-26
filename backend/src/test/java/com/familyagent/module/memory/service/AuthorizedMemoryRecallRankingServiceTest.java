package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.entity.MemoryEntry;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizedMemoryRecallRankingServiceTest {

    @Mock private AuthorizedMemoryRecallEmbeddingService embeddingService;
    @Mock private MemoryRecallVectorRepository vectorRepository;

    @Test
    void rankPropagatesEmbeddingObservationWithoutVectorCandidates() {
        EmbeddingCallObservation observation = observation();
        when(embeddingService.embed(10L, "bedtime reminder"))
                .thenReturn(new AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding(
                        Collections.nCopies(1536, 0.01), observation));
        AuthorizedMemoryRecallRankingService service = service();

        AuthorizedMemoryRecallRankingService.RankedRecall result = service.rank(
                10L, "bedtime reminder", List.of(), List.of(), List.of(), 3, 3, 1L);

        assertEquals(observation, result.embeddingObservation());
        verifyNoInteractions(vectorRepository);
    }

    @Test
    void rankUsesUnifiedMemoryEntryIdForDiaryVectorIndex() {
        EmbeddingCallObservation observation = observation();
        List<Double> values = Collections.nCopies(1536, 0.01);
        AuthorizedMemoryRecallCandidate diary = diary(144L, 44L, "bedtime reminder");
        when(embeddingService.embed(10L, "bedtime reminder"))
                .thenReturn(new AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding(values, observation));
        when(vectorRepository.rankSourceIds(10L, "MEMORY", List.of(144L), values, 0.72, 3))
                .thenReturn(List.of(144L));

        AuthorizedMemoryRecallRankingService.RankedRecall result = service().rank(
                10L, "bedtime reminder", List.of(diary), List.of(), List.of(), 3, 3, 1L);

        assertTrue(result.usedVector());
        assertEquals(List.of(diary), result.diaries());
        verify(vectorRepository).rankSourceIds(10L, "MEMORY", List.of(144L), values, 0.72, 3);
    }

    @Test
    void rankFallsBackToTextWhenVectorRepositoryFails() {
        EmbeddingCallObservation observation = observation();
        List<Double> values = Collections.nCopies(1536, 0.01);
        AuthorizedMemoryRecallCandidate diary = diary(144L, 44L, "bedtime reminder");
        when(embeddingService.embed(10L, "bedtime reminder"))
                .thenReturn(new AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding(values, observation));
        when(vectorRepository.rankSourceIds(10L, "MEMORY", List.of(144L), values, 0.72, 3))
                .thenThrow(new IllegalStateException("database detail"));

        AuthorizedMemoryRecallRankingService.RankedRecall result = service().rank(
                10L, "bedtime reminder", List.of(diary), List.of(), List.of(), 3, 3, 1L);

        assertFalse(result.usedVector());
        assertEquals(List.of(diary), result.diaries());
        assertEquals(observation, result.embeddingObservation());
    }

    private AuthorizedMemoryRecallRankingService service() {
        return new AuthorizedMemoryRecallRankingService(
                embeddingService,
                vectorRepository,
                new AuthorizedMemoryRecallTextRanker(new AuthorizedMemoryRecallScorer()));
    }

    private static EmbeddingCallObservation observation() {
        return new EmbeddingCallObservation(
                true, true, false, "local", "local/hash-embedding", 1536, 18L, null);
    }

    private static AuthorizedMemoryRecallCandidate diary(Long id, Long originId, String content) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setLibraryKind("FAMILY");
        entry.setOriginType(MemoryOriginType.DIARY.name());
        entry.setOriginId(originId);
        entry.setContent(content);
        entry.setOccurredAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        return AuthorizedMemoryRecallCandidate.from(entry);
    }
}
