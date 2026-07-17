package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.entity.MemoryEntry;
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
            List<DiaryEntry> diaryCandidates,
            List<MemoryEntry> memoryCandidates,
            List<GrowthGuardRecord> growthCandidates,
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
            List<DiaryEntry> diaryCandidates,
            List<MemoryEntry> memoryCandidates,
            List<GrowthGuardRecord> growthCandidates,
            int diaryLimit,
            int memoryLimit,
            long readyEmbeddings) {
        if (readyEmbeddings <= 0 || query.isBlank()) {
            return VectorCandidateIds.empty(null);
        }

        AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding embedding = embeddingService.embed(
                familyId,
                query);
        EmbeddingCallObservation observation = embedding.observation();
        if (!observation.success()) {
            return VectorCandidateIds.empty(observation);
        }

        List<Double> values = embedding.values();
        try {
            return new VectorCandidateIds(
                    rankSourceIds(
                            familyId,
                            "DIARY",
                            diaryCandidates.stream().map(DiaryEntry::getId).toList(),
                            values,
                            diaryLimit),
                    rankSourceIds(
                            familyId,
                            "MEMORY",
                            memoryCandidates.stream().map(MemoryEntry::getId).toList(),
                            values,
                            memoryLimit),
                    rankSourceIds(
                            familyId,
                            "GROWTH_OBSERVATION",
                            growthCandidates.stream().map(GrowthGuardRecord::getId).toList(),
                            values,
                            memoryLimit),
                    observation);
        } catch (RuntimeException error) {
            log.warn(
                    "Vector memory recall failed, using text fallback: errorType={}",
                    error.getClass().getSimpleName());
            return VectorCandidateIds.empty(observation);
        }
    }

    private List<Long> rankSourceIds(
            Long familyId,
            String sourceType,
            List<Long> sourceIds,
            List<Double> values,
            int limit) {
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        return vectorRepository.rankSourceIds(
                familyId,
                sourceType,
                sourceIds,
                values,
                MAX_VECTOR_DISTANCE,
                limit);
    }

    public record RankedRecall(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords,
            boolean usedVector,
            EmbeddingCallObservation embeddingObservation) {

        public RankedRecall(
                List<DiaryEntry> diaries,
                List<MemoryEntry> memories,
                List<GrowthGuardRecord> growthRecords,
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
