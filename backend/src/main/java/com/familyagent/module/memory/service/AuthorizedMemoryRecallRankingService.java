package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryEmbeddingSourceType;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.repository.MemoryRecallVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizedMemoryRecallRankingService {

    public static final String ALGORITHM_VERSION = "authorized-memory-recall.v1";
    private static final double MAX_VECTOR_DISTANCE = 0.72;

    private final AuthorizedMemoryRecallEmbeddingService embeddingService;
    private final MemoryRecallVectorRepository vectorRepository;
    private final AuthorizedMemoryRecallTextRanker textRanker;

    public RankedRecall rank(
            Long familyId,
            String query,
            List<AuthorizedMemoryRecallCandidate> diaryCandidates,
            List<AuthorizedMemoryRecallCandidate> memoryCandidates,
            List<AuthorizedMemoryRecallCandidate> growthCandidates,
            int diaryLimit,
            int memoryLimit,
            long readyEmbeddings) {
        VectorCandidateIds vectorCandidates = loadVectorCandidates(
                familyId,
                query,
                diaryCandidates,
                memoryCandidates,
                growthCandidates,
                diaryLimit,
                memoryLimit,
                readyEmbeddings);
        AuthorizedMemoryRecallTextRanker.RankedCandidates ranked = textRanker.rank(
                diaryCandidates,
                memoryCandidates,
                growthCandidates,
                vectorCandidates.diaryIds(),
                vectorCandidates.memoryIds(),
                vectorCandidates.growthIds(),
                query,
                diaryLimit,
                memoryLimit);
        return new RankedRecall(
                ranked.diaries(),
                ranked.memories(),
                ranked.growthRecords(),
                ranked.usedVector(),
                vectorCandidates.embeddingObservation());
    }

    private VectorCandidateIds loadVectorCandidates(
            Long familyId,
            String query,
            List<AuthorizedMemoryRecallCandidate> diaryCandidates,
            List<AuthorizedMemoryRecallCandidate> memoryCandidates,
            List<AuthorizedMemoryRecallCandidate> growthCandidates,
            int diaryLimit,
            int memoryLimit,
            long readyEmbeddings) {
        if (readyEmbeddings <= 0 || query.isBlank()) {
            return VectorCandidateIds.empty(null);
        }

        AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding embedding = embeddingService.embed(familyId, query);
        EmbeddingCallObservation observation = embedding.observation();
        if (!observation.success()) {
            return VectorCandidateIds.empty(observation);
        }

        try {
            return new VectorCandidateIds(
                    rankSourceIds(familyId, diaryCandidates, embedding.values(), diaryLimit),
                    rankSourceIds(familyId, memoryCandidates, embedding.values(), memoryLimit),
                    rankSourceIds(familyId, growthCandidates, embedding.values(), memoryLimit),
                    observation);
        } catch (RuntimeException error) {
            log.warn("Vector memory recall failed, using text fallback: errorType={}",
                    error.getClass().getSimpleName());
            return VectorCandidateIds.empty(observation);
        }
    }

    private List<Long> rankSourceIds(
            Long familyId,
            List<AuthorizedMemoryRecallCandidate> candidates,
            List<Double> values,
            int limit) {
        List<Long> sourceIds = candidates.stream()
                .map(AuthorizedMemoryRecallCandidate::embeddingSourceId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        return vectorRepository.rankSourceIds(
                familyId,
                MemoryEmbeddingSourceType.MEMORY.name(),
                sourceIds,
                values,
                MAX_VECTOR_DISTANCE,
                limit);
    }

    public record RankedRecall(
            List<AuthorizedMemoryRecallCandidate> diaries,
            List<AuthorizedMemoryRecallCandidate> memories,
            List<AuthorizedMemoryRecallCandidate> growthRecords,
            boolean usedVector,
            EmbeddingCallObservation embeddingObservation) {

        public RankedRecall(
                List<AuthorizedMemoryRecallCandidate> diaries,
                List<AuthorizedMemoryRecallCandidate> memories,
                List<AuthorizedMemoryRecallCandidate> growthRecords,
                boolean usedVector) {
            this(diaries, memories, growthRecords, usedVector, null);
        }
    }

    private record VectorCandidateIds(
            List<Long> diaryIds,
            List<Long> memoryIds,
            List<Long> growthIds,
            EmbeddingCallObservation embeddingObservation) {

        private static VectorCandidateIds empty(EmbeddingCallObservation observation) {
            return new VectorCandidateIds(List.of(), List.of(), List.of(), observation);
        }
    }
}
