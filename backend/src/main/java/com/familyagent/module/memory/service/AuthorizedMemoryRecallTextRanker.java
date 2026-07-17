package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.entity.MemoryEntry;
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

@Component
public class AuthorizedMemoryRecallTextRanker {

    public RankedCandidates rank(
            List<DiaryEntry> diaryCandidates,
            List<MemoryEntry> memoryCandidates,
            List<GrowthGuardRecord> growthCandidates,
            List<Long> diaryVectorIds,
            List<Long> memoryVectorIds,
            List<Long> growthVectorIds,
            String query,
            int diaryLimit,
            int memoryLimit) {
        List<Long> supportedDiaryIds = supportedDiaryIds(diaryCandidates, diaryVectorIds, query);
        List<Long> supportedMemoryIds = supportedMemoryIds(memoryCandidates, memoryVectorIds, query);
        List<Long> supportedGrowthIds = supportedGrowthIds(growthCandidates, growthVectorIds, query);
        return new RankedCandidates(
                mergeDiaries(diaryCandidates, supportedDiaryIds, query, diaryLimit),
                mergeMemories(memoryCandidates, supportedMemoryIds, query, memoryLimit),
                mergeGrowthRecords(growthCandidates, supportedGrowthIds, query, memoryLimit),
                !supportedDiaryIds.isEmpty()
                        || !supportedMemoryIds.isEmpty()
                        || !supportedGrowthIds.isEmpty());
    }

    private static List<Long> supportedDiaryIds(
            List<DiaryEntry> candidates,
            List<Long> vectorIds,
            String query) {
        Map<Long, DiaryEntry> byId = new LinkedHashMap<>();
        for (DiaryEntry entry : candidates) {
            if (entry != null && entry.getId() != null) {
                byId.put(entry.getId(), entry);
            }
        }
        return vectorIds.stream()
                .filter(id -> {
                    DiaryEntry entry = byId.get(id);
                    return entry != null && hasSupport(diarySearchText(entry), entry.getMetadata(), query);
                })
                .toList();
    }

    private static List<Long> supportedMemoryIds(
            List<MemoryEntry> candidates,
            List<Long> vectorIds,
            String query) {
        Map<Long, MemoryEntry> byId = new LinkedHashMap<>();
        for (MemoryEntry entry : candidates) {
            if (entry != null && entry.getId() != null) {
                byId.put(entry.getId(), entry);
            }
        }
        return vectorIds.stream()
                .filter(id -> {
                    MemoryEntry entry = byId.get(id);
                    return entry != null && hasSupport(memorySearchText(entry), entry.getMetadata(), query);
                })
                .toList();
    }

    private static List<Long> supportedGrowthIds(
            List<GrowthGuardRecord> candidates,
            List<Long> vectorIds,
            String query) {
        Map<Long, GrowthGuardRecord> byId = new LinkedHashMap<>();
        for (GrowthGuardRecord record : candidates) {
            if (record != null && record.getId() != null) {
                byId.put(record.getId(), record);
            }
        }
        return vectorIds.stream()
                .filter(id -> {
                    GrowthGuardRecord record = byId.get(id);
                    return record != null && hasSupport(growthSearchText(record), record.getMetadata(), query);
                })
                .toList();
    }

    private static boolean hasSupport(String text, Object metadata, String query) {
        return query != null
                && !query.isBlank()
                && (score(text, query) > 0 || MemoryIndexMetadataBuilder.indexBoost(metadata, query) > 0);
    }

    private static List<DiaryEntry> mergeDiaries(
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

    private static List<MemoryEntry> mergeMemories(
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

    private static List<GrowthGuardRecord> mergeGrowthRecords(
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

    private static List<GrowthGuardRecord> rankGrowthRecords(
            List<GrowthGuardRecord> entries,
            String query,
            int limit) {
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
        int score = target.contains(query) ? 20 : 0;
        for (String token : query.split("\\s+")) {
            if (token.length() >= 2 && target.contains(token)) {
                score += Math.min(10, token.length());
            }
        }
        for (int index = 0; index < query.length(); index += 2) {
            String piece = query.substring(index, Math.min(index + 2, query.length()));
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
                + " " + String.join(" ", entry.getTags() == null ? new String[0] : entry.getTags());
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

    public record RankedCandidates(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords,
            boolean usedVector) {
    }
}
