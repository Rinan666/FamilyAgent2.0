package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryRecallVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizedMemoryRecallRankingService {

    public static final String ALGORITHM_VERSION = "authorized-memory-recall.v1";
    private static final double MAX_VECTOR_DISTANCE = 0.72;

    private final AuthorizedMemoryRecallEmbeddingService embeddingService;
    private final MemoryRecallVectorRepository vectorRepository;

    public RankedRecall rank(
            Long familyId,
            String query,
            List<DiaryEntry> diaryCandidates,
            List<MemoryEntry> memoryCandidates,
            List<GrowthGuardRecord> growthCandidates,
            int diaryLimit,
            int memoryLimit,
            long readyEmbeddings) {
        VectorRanking vectorRanking = vectorRank(
                familyId,
                query,
                diaryCandidates,
                memoryCandidates,
                growthCandidates,
                diaryLimit,
                memoryLimit,
                readyEmbeddings);
        if (vectorRanking.usedVector()) {
            return new RankedRecall(
                    vectorRanking.diaries(),
                    vectorRanking.memories(),
                    vectorRanking.growthRecords(),
                    true,
                    vectorRanking.embeddingObservation());
        }
        return new RankedRecall(
                rankDiaries(diaryCandidates, query, diaryLimit),
                rankMemories(memoryCandidates, query, memoryLimit),
                rankGrowthRecords(growthCandidates, query, memoryLimit),
                false,
                vectorRanking.embeddingObservation());
    }

    private VectorRanking vectorRank(
            Long familyId,
            String query,
            List<DiaryEntry> diaryCandidates,
            List<MemoryEntry> memoryCandidates,
            List<GrowthGuardRecord> growthCandidates,
            int diaryLimit,
            int memoryLimit,
            long readyEmbeddings) {
        if (readyEmbeddings <= 0 || query.isBlank()) {
            return VectorRanking.empty(null);
        }

        AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding embedding = embeddingService.embed(
                familyId,
                query);
        EmbeddingCallObservation observation = embedding.observation();
        if (!observation.success()) {
            return VectorRanking.empty(observation);
        }

        List<Double> values = embedding.values();
        try {
            List<Long> diaryIds = rankSourceIds(
                    familyId,
                    "DIARY",
                    diaryCandidates.stream().map(DiaryEntry::getId).toList(),
                    values,
                    diaryLimit);
            List<Long> memoryIds = rankSourceIds(
                    familyId,
                    "MEMORY",
                    memoryCandidates.stream().map(MemoryEntry::getId).toList(),
                    values,
                    memoryLimit);
            List<Long> growthIds = rankSourceIds(
                    familyId,
                    "GROWTH_OBSERVATION",
                    growthCandidates.stream().map(GrowthGuardRecord::getId).toList(),
                    values,
                    memoryLimit);
            diaryIds = filterSupportedDiaryIds(diaryCandidates, diaryIds, query);
            memoryIds = filterSupportedMemoryIds(memoryCandidates, memoryIds, query);
            growthIds = filterSupportedGrowthIds(growthCandidates, growthIds, query);

            List<DiaryEntry> diaries = mergeVectorAndTextDiaries(diaryCandidates, diaryIds, query, diaryLimit);
            List<MemoryEntry> memories = mergeVectorAndTextMemories(memoryCandidates, memoryIds, query, memoryLimit);
            List<GrowthGuardRecord> growthRecords = mergeVectorAndTextGrowthRecords(growthCandidates, growthIds, query, memoryLimit);
            return new VectorRanking(
                    diaries,
                    memories,
                    growthRecords,
                    !diaryIds.isEmpty() || !memoryIds.isEmpty() || !growthIds.isEmpty(),
                    observation);
        } catch (RuntimeException error) {
            log.warn("Vector memory recall failed, using text fallback: errorType={}",
                    error.getClass().getSimpleName());
            return VectorRanking.empty(observation);
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

    private static List<Long> filterSupportedDiaryIds(List<DiaryEntry> candidates, List<Long> vectorIds, String query) {
        Map<Long, DiaryEntry> byId = new LinkedHashMap<>();
        for (DiaryEntry entry : candidates) {
            if (entry != null && entry.getId() != null) {
                byId.put(entry.getId(), entry);
            }
        }
        return vectorIds.stream()
                .filter(id -> {
                    DiaryEntry entry = byId.get(id);
                    return entry != null && hasTextOrIndexSupport(diarySearchText(entry), entry.getMetadata(), query);
                })
                .toList();
    }

    private static List<Long> filterSupportedMemoryIds(List<MemoryEntry> candidates, List<Long> vectorIds, String query) {
        Map<Long, MemoryEntry> byId = new LinkedHashMap<>();
        for (MemoryEntry entry : candidates) {
            if (entry != null && entry.getId() != null) {
                byId.put(entry.getId(), entry);
            }
        }
        return vectorIds.stream()
                .filter(id -> {
                    MemoryEntry entry = byId.get(id);
                    return entry != null && hasTextOrIndexSupport(memorySearchText(entry), entry.getMetadata(), query);
                })
                .toList();
    }

    private static List<Long> filterSupportedGrowthIds(List<GrowthGuardRecord> candidates, List<Long> vectorIds, String query) {
        Map<Long, GrowthGuardRecord> byId = new LinkedHashMap<>();
        for (GrowthGuardRecord record : candidates) {
            if (record != null && record.getId() != null) {
                byId.put(record.getId(), record);
            }
        }
        return vectorIds.stream()
                .filter(id -> {
                    GrowthGuardRecord record = byId.get(id);
                    return record != null && hasTextOrIndexSupport(growthSearchText(record), record.getMetadata(), query);
                })
                .toList();
    }

    private static boolean hasTextOrIndexSupport(String text, Object metadata, String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return score(text, query) > 0 || MemoryIndexMetadataBuilder.indexBoost(metadata, query) > 0;
    }

    private static List<DiaryEntry> mergeVectorAndTextDiaries(
            List<DiaryEntry> candidates,
            List<Long> vectorIds,
            String query,
            int limit) {
        Map<Long, DiaryEntry> byId = new LinkedHashMap<>();
        for (DiaryEntry entry : candidates) {
            if (entry != null && entry.getId() != null) {
                byId.put(entry.getId(), entry);
            }
        }
        List<DiaryEntry> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Long id : vectorIds) {
            DiaryEntry entry = byId.get(id);
            if (entry != null && used.add(id)) {
                result.add(entry);
            }
        }
        for (DiaryEntry entry : rankDiaries(candidates, query, limit)) {
            if (entry.getId() != null && used.add(entry.getId())) {
                result.add(entry);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result.stream()
                .sorted(Comparator
                        .comparingDouble((DiaryEntry entry) -> weightedDiaryScore(entry, query)).reversed()
                        .thenComparing(DiaryEntry::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private static List<MemoryEntry> mergeVectorAndTextMemories(
            List<MemoryEntry> candidates,
            List<Long> vectorIds,
            String query,
            int limit) {
        Map<Long, MemoryEntry> byId = new LinkedHashMap<>();
        for (MemoryEntry entry : candidates) {
            if (entry != null && entry.getId() != null) {
                byId.put(entry.getId(), entry);
            }
        }
        List<MemoryEntry> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Long id : vectorIds) {
            MemoryEntry entry = byId.get(id);
            if (entry != null && used.add(id)) {
                result.add(entry);
            }
        }
        for (MemoryEntry entry : rankMemories(candidates, query, limit)) {
            if (entry.getId() != null && used.add(entry.getId())) {
                result.add(entry);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result.stream()
                .sorted(Comparator
                        .comparingDouble((MemoryEntry entry) -> weightedMemoryScore(entry, query)).reversed()
                        .thenComparing(MemoryEntry::getImportance, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MemoryEntry::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private static List<GrowthGuardRecord> mergeVectorAndTextGrowthRecords(
            List<GrowthGuardRecord> candidates,
            List<Long> vectorIds,
            String query,
            int limit) {
        Map<Long, GrowthGuardRecord> byId = new LinkedHashMap<>();
        for (GrowthGuardRecord record : candidates) {
            if (record != null && record.getId() != null) {
                byId.put(record.getId(), record);
            }
        }
        List<GrowthGuardRecord> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Long id : vectorIds) {
            GrowthGuardRecord record = byId.get(id);
            if (record != null && used.add(id)) {
                result.add(record);
            }
        }
        for (GrowthGuardRecord record : rankGrowthRecords(candidates, query, limit)) {
            if (record.getId() != null && used.add(record.getId())) {
                result.add(record);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result.stream()
                .sorted(Comparator
                        .comparingDouble((GrowthGuardRecord record) -> weightedGrowthScore(record, query)).reversed()
                        .thenComparing(GrowthGuardRecord::getSeverity, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(GrowthGuardRecord::getObservedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(GrowthGuardRecord::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private static List<DiaryEntry> rankDiaries(List<DiaryEntry> entries, String query, int limit) {
        return entries.stream()
                .filter(Objects::nonNull)
                .filter(entry -> query.isBlank() || weightedDiaryScore(entry, query) > 0)
                .sorted(Comparator
                        .comparingDouble((DiaryEntry entry) -> weightedDiaryScore(entry, query)).reversed()
                        .thenComparing(DiaryEntry::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private static List<MemoryEntry> rankMemories(List<MemoryEntry> entries, String query, int limit) {
        return entries.stream()
                .filter(Objects::nonNull)
                .filter(entry -> query.isBlank() || weightedMemoryScore(entry, query) > 0)
                .sorted(Comparator
                        .comparingDouble((MemoryEntry entry) -> weightedMemoryScore(entry, query)).reversed()
                        .thenComparing(MemoryEntry::getImportance, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MemoryEntry::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private static List<GrowthGuardRecord> rankGrowthRecords(List<GrowthGuardRecord> entries, String query, int limit) {
        return entries.stream()
                .filter(Objects::nonNull)
                .filter(record -> query.isBlank() || weightedGrowthScore(record, query) > 0)
                .sorted(Comparator
                        .comparingDouble((GrowthGuardRecord record) -> weightedGrowthScore(record, query)).reversed()
                        .thenComparing(GrowthGuardRecord::getSeverity, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(GrowthGuardRecord::getObservedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(GrowthGuardRecord::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private static double weightedDiaryScore(DiaryEntry entry, String query) {
        int baseScore = score(diarySearchText(entry), query)
                + MemoryIndexMetadataBuilder.indexBoost(entry.getMetadata(), query);
        return baseScore * MemoryIndexMetadataBuilder.relevanceWeight(entry.getMetadata(), entry.getCreatedAt());
    }

    private static double weightedMemoryScore(MemoryEntry entry, String query) {
        int baseScore = score(memorySearchText(entry), query)
                + MemoryIndexMetadataBuilder.indexBoost(entry.getMetadata(), query);
        return baseScore * MemoryIndexMetadataBuilder.relevanceWeight(
                entry.getMetadata(),
                entry.getUpdatedAt() == null ? entry.getCreatedAt() : entry.getUpdatedAt());
    }

    private static double weightedGrowthScore(GrowthGuardRecord record, String query) {
        int baseScore = score(growthSearchText(record), query)
                + MemoryIndexMetadataBuilder.indexBoost(record.getMetadata(), query);
        return baseScore * MemoryIndexMetadataBuilder.relevanceWeight(
                record.getMetadata(),
                record.getObservedAt() == null ? record.getCreatedAt() : record.getObservedAt().atStartOfDay());
    }

    private static int score(String text, String query) {
        if (query.isBlank()) {
            return 0;
        }
        String target = normalize(text);
        int score = 0;
        if (target.contains(query)) {
            score += 20;
        }
        for (String token : query.split("\\s+")) {
            if (token.length() < 2) {
                continue;
            }
            if (target.contains(token)) {
                score += Math.min(10, token.length());
            }
        }
        for (int i = 0; i < query.length(); i += 2) {
            String piece = query.substring(i, Math.min(i + 2, query.length()));
            if (piece.length() == 2 && target.contains(piece)) {
                score += 1;
            }
        }
        return score;
    }

    private static String diarySearchText(DiaryEntry entry) {
        return entry.getRawText()
                + " " + mapText(entry.getStructured())
                + " " + mapText(entry.getMetadata())
                + " " + String.join(" ", safeTags(entry));
    }

    private static String memorySearchText(MemoryEntry entry) {
        return entry.getContent() + " " + entry.getSummary() + " " + mapText(entry.getMetadata());
    }

    private static String growthSearchText(GrowthGuardRecord record) {
        return record.getContent()
                + " " + record.getCategory()
                + " " + record.getSeverity()
                + " " + record.getObservedAt()
                + " " + mapText(record.getMetadata());
    }

    private static String[] safeTags(DiaryEntry entry) {
        return entry.getTags() == null ? new String[0] : entry.getTags();
    }

    private static String mapText(Object value) {
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder();
            for (Object item : map.values()) {
                if (item != null) {
                    builder.append(item).append(' ');
                }
            }
            return builder.toString();
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}，。！？；：“”‘’（）【】《》]", " ")
                .replaceAll("\\s+", " ")
                .trim();
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

    private record VectorRanking(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords,
            boolean usedVector,
            EmbeddingCallObservation embeddingObservation) {
        private static VectorRanking empty(EmbeddingCallObservation observation) {
            return new VectorRanking(List.of(), List.of(), List.of(), false, observation);
        }
    }
}
