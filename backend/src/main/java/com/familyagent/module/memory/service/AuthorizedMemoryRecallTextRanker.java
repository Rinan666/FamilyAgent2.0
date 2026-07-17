package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.entity.MemoryEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuthorizedMemoryRecallTextRanker {

    private final AuthorizedMemoryRecallScorer scorer;

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

    private List<Long> supportedDiaryIds(
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
                    return entry != null && scorer.supports(entry, query);
                })
                .toList();
    }

    private List<Long> supportedMemoryIds(
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
                    return entry != null && scorer.supports(entry, query);
                })
                .toList();
    }

    private List<Long> supportedGrowthIds(
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
                    return record != null && scorer.supports(record, query);
                })
                .toList();
    }

    private List<DiaryEntry> mergeDiaries(
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
                        .comparingDouble((DiaryEntry entry) -> scorer.score(entry, query)).reversed()
                        .thenComparing(DiaryEntry::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private List<MemoryEntry> mergeMemories(
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
                        .comparingDouble((MemoryEntry entry) -> scorer.score(entry, query)).reversed()
                        .thenComparing(MemoryEntry::getImportance, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MemoryEntry::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private List<GrowthGuardRecord> mergeGrowthRecords(
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
                        .comparingDouble((GrowthGuardRecord record) -> scorer.score(record, query)).reversed()
                        .thenComparing(GrowthGuardRecord::getSeverity, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(GrowthGuardRecord::getObservedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(GrowthGuardRecord::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private List<DiaryEntry> rankDiaries(List<DiaryEntry> entries, String query, int limit) {
        return entries.stream()
                .filter(Objects::nonNull)
                .filter(entry -> query.isBlank() || scorer.score(entry, query) > 0)
                .sorted(Comparator
                        .comparingDouble((DiaryEntry entry) -> scorer.score(entry, query)).reversed()
                        .thenComparing(DiaryEntry::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private List<MemoryEntry> rankMemories(List<MemoryEntry> entries, String query, int limit) {
        return entries.stream()
                .filter(Objects::nonNull)
                .filter(entry -> query.isBlank() || scorer.score(entry, query) > 0)
                .sorted(Comparator
                        .comparingDouble((MemoryEntry entry) -> scorer.score(entry, query)).reversed()
                        .thenComparing(MemoryEntry::getImportance, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MemoryEntry::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private List<GrowthGuardRecord> rankGrowthRecords(
            List<GrowthGuardRecord> entries,
            String query,
            int limit) {
        return entries.stream()
                .filter(Objects::nonNull)
                .filter(record -> query.isBlank() || scorer.score(record, query) > 0)
                .sorted(Comparator
                        .comparingDouble((GrowthGuardRecord record) -> scorer.score(record, query)).reversed()
                        .thenComparing(GrowthGuardRecord::getSeverity, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(GrowthGuardRecord::getObservedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(GrowthGuardRecord::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    public record RankedCandidates(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords,
            boolean usedVector) {
    }
}
