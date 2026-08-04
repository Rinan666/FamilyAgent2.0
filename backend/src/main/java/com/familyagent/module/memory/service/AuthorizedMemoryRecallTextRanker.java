package com.familyagent.module.memory.service;

import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuthorizedMemoryRecallTextRanker {

    private final AuthorizedMemoryRecallScorer scorer;

    public RankedCandidates rank(
            List<AuthorizedMemoryRecallCandidate> diaryCandidates,
            List<AuthorizedMemoryRecallCandidate> memoryCandidates,
            List<AuthorizedMemoryRecallCandidate> growthCandidates,
            List<Long> diaryVectorIds,
            List<Long> memoryVectorIds,
            List<Long> growthVectorIds,
            String query,
            int diaryLimit,
            int memoryLimit) {
        MergeResult diaries = merge(diaryCandidates, diaryVectorIds, query, diaryLimit);
        MergeResult memories = merge(memoryCandidates, memoryVectorIds, query, memoryLimit);
        MergeResult growth = merge(growthCandidates, growthVectorIds, query, memoryLimit);
        return new RankedCandidates(
                diaries.candidates(),
                memories.candidates(),
                growth.candidates(),
                diaries.usedVector() || memories.usedVector() || growth.usedVector());
    }

    public List<AuthorizedMemoryRecallCandidate> rankUnified(
            List<AuthorizedMemoryRecallCandidate> candidates,
            List<Long> vectorIds,
            String query,
            int limit) {
        return merge(candidates, vectorIds, query, limit).candidates();
    }

    private MergeResult merge(
            List<AuthorizedMemoryRecallCandidate> candidates,
            List<Long> vectorIds,
            String query,
            int limit) {
        Map<Long, AuthorizedMemoryRecallCandidate> bySourceId = new LinkedHashMap<>();
        candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.embeddingSourceId() != null)
                .forEach(candidate -> bySourceId.put(candidate.embeddingSourceId(), candidate));

        List<AuthorizedMemoryRecallCandidate> result = new ArrayList<>();
        Set<Long> used = new LinkedHashSet<>();
        for (Long sourceId : vectorIds) {
            AuthorizedMemoryRecallCandidate candidate = bySourceId.get(sourceId);
            if (candidate != null && scorer.supports(candidate, query) && used.add(sourceId)) {
                result.add(candidate);
            }
        }
        boolean usedVector = !result.isEmpty();

        candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> query.isBlank() || scorer.score(candidate, query) > 0)
                .sorted(candidateComparator(query))
                .forEach(candidate -> {
                    if (result.size() < limit && candidate.embeddingSourceId() != null
                            && used.add(candidate.embeddingSourceId())) {
                        result.add(candidate);
                    }
                });

        return new MergeResult(
                result.stream().sorted(candidateComparator(query)).limit(limit).toList(),
                usedVector);
    }

    private Comparator<AuthorizedMemoryRecallCandidate> candidateComparator(String query) {
        return Comparator
                .comparingDouble((AuthorizedMemoryRecallCandidate candidate) -> scorer.score(candidate, query))
                .reversed()
                .thenComparing(
                        candidate -> candidate.entry().getImportance(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                        candidate -> candidate.entry().getOccurredAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                        candidate -> candidate.entry().getUpdatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private record MergeResult(List<AuthorizedMemoryRecallCandidate> candidates, boolean usedVector) {
    }

    public record RankedCandidates(
            List<AuthorizedMemoryRecallCandidate> diaries,
            List<AuthorizedMemoryRecallCandidate> memories,
            List<AuthorizedMemoryRecallCandidate> growthRecords,
            boolean usedVector) {
    }
}
