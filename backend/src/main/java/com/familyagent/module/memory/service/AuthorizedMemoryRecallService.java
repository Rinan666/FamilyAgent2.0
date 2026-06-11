package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.dto.GrowthStalenessStats;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.repository.GrowthGuardStalenessVoteRepository;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.dto.MemoryVoteStats;
import com.familyagent.module.memory.dto.RecallSourceSummary;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEmbeddingRepository;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
@Service
@RequiredArgsConstructor
public class AuthorizedMemoryRecallService {

    private static final int CANDIDATE_MULTIPLIER = 5;
    private static final double MAX_VECTOR_DISTANCE = 0.72;
    private static final List<String> FAMILY_RELEVANCE_TERMS = List.of(
            "family", "diary", "memory", "growth", "parent", "child", "study",
            "tooth", "teeth", "dental", "screen", "sleep", "health", "exercise", "emotion",
            "家族", "家庭", "家人", "家里", "我家", "我们家", "家长", "爸", "妈", "爷", "奶", "外公", "外婆",
            "孩子", "儿子", "女儿", "孙", "长辈", "亲子", "关系", "沟通", "日记", "记录",
            "记忆", "经验", "沉淀", "传承", "故事", "成长", "观察", "情绪", "焦虑", "压力",
            "学习", "作业", "考试", "升学", "志愿", "学校", "选择", "复盘", "后悔", "健康",
            "牙", "刷牙", "视力", "睡眠", "运动", "体态", "手机", "屏幕", "习惯", "陪伴",
            "教育", "保存", "记下来", "想起来");

    private final DiaryEntryRepository diaryRepository;
    private final MemoryEntryRepository memoryRepository;
    private final MemoryEntryVoteRepository memoryVoteRepository;
    private final GrowthGuardRecordRepository growthRecordRepository;
    private final GrowthGuardStalenessVoteRepository stalenessVoteRepository;
    private final MemoryEmbeddingRepository embeddingRepository;
    private final AIServiceClient aiServiceClient;
    private final JdbcTemplate jdbcTemplate;
    private final FamilyService familyService;

    public AuthorizedMemoryRecallResult recallForFamily(
            Long familyId,
            Long viewerUserId,
            String query,
            int diaryLimit,
            int memoryLimit) {
        return recallForFamily(familyId, viewerUserId, query, null, diaryLimit, memoryLimit);
    }

    public AuthorizedMemoryRecallResult recallForFamily(
            Long familyId,
            Long viewerUserId,
            String query,
            String scene,
            int diaryLimit,
            int memoryLimit) {
        familyService.checkMembership(familyId);
        return recallForFamilyAfterViewerValidated(familyId, viewerUserId, query, scene, diaryLimit, memoryLimit);
    }

    /**
     * Reuses the normal family recall path after the caller has already verified
     * both access and the viewer's family membership.
     */
    public AuthorizedMemoryRecallResult recallForFamilyAfterViewerValidated(
            Long familyId,
            Long viewerUserId,
            String query,
            int diaryLimit,
            int memoryLimit) {
        return recallForFamilyAfterViewerValidated(familyId, viewerUserId, query, null, diaryLimit, memoryLimit);
    }

    public AuthorizedMemoryRecallResult recallForFamilyAfterViewerValidated(
            Long familyId,
            Long viewerUserId,
            String query,
            String scene,
            int diaryLimit,
            int memoryLimit) {
        String normalizedQuery = normalize(query);
        if (!shouldRecallFamilyContext(normalizedQuery, scene)) {
            return emptyRecall(normalizedQuery, "SKIPPED_UNRELATED_QUERY");
        }
        int diaryCandidateLimit = Math.max(diaryLimit * CANDIDATE_MULTIPLIER, diaryLimit);
        int memoryCandidateLimit = Math.max(memoryLimit * CANDIDATE_MULTIPLIER, memoryLimit);

        List<DiaryEntry> diaryCandidates = diaryRepository.findVisibleByFamily(
                familyId,
                viewerUserId,
                diaryCandidateLimit);
        List<MemoryEntry> memoryCandidates = memoryRepository.findActiveFamilyMemories(
                familyId,
                viewerUserId,
                memoryCandidateLimit);
        List<GrowthGuardRecord> growthCandidates = growthRecordRepository.findVisibleByFamily(
                familyId,
                viewerUserId,
                memoryCandidateLimit);
        attachSocialWeights(memoryCandidates, growthCandidates, viewerUserId);

        long readyEmbeddings = embeddingRepository.countReadyByFamilyId(familyId);
        VectorRanking vectorRanking = vectorRank(
                familyId,
                normalizedQuery,
                diaryCandidates,
                memoryCandidates,
                growthCandidates,
                diaryLimit,
                memoryLimit,
                readyEmbeddings);

        List<DiaryEntry> diaries = vectorRanking.usedVector()
                ? vectorRanking.diaries()
                : rankDiaries(diaryCandidates, normalizedQuery, diaryLimit);
        List<MemoryEntry> memories = vectorRanking.usedVector()
                ? vectorRanking.memories()
                : rankMemories(memoryCandidates, normalizedQuery, memoryLimit);
        List<GrowthGuardRecord> growthRecords = vectorRanking.usedVector()
                ? vectorRanking.growthRecords()
                : rankGrowthRecords(growthCandidates, normalizedQuery, memoryLimit);

        return AuthorizedMemoryRecallResult.builder()
                .diaries(diaries)
                .memories(memories)
                .growthRecords(growthRecords)
                .diaryCount(diaries.size())
                .memoryCount(memories.size())
                .growthRecordCount(growthRecords.size())
                .sources(buildSourceSummaries(diaries, memories, growthRecords))
                .query(normalizedQuery)
                .embeddingReadyCount(readyEmbeddings)
                .retrievalMode(vectorRanking.usedVector() ? "VECTOR_WITH_TEXT_FALLBACK" : "TEXT_FALLBACK")
                .build();
    }

    public AuthorizedMemoryRecallResult recallForMirror(
            Long familyId,
            Long targetUserId,
            Long viewerUserId,
            String query,
            int diaryLimit,
            int memoryLimit) {
        String normalizedQuery = normalize(query);
        if (!shouldRecallFamilyContext(normalizedQuery, null)) {
            return emptyRecall(normalizedQuery, "SKIPPED_UNRELATED_QUERY");
        }
        int diaryCandidateLimit = Math.max(diaryLimit * CANDIDATE_MULTIPLIER, diaryLimit);
        int memoryCandidateLimit = Math.max(memoryLimit * CANDIDATE_MULTIPLIER, memoryLimit);

        List<DiaryEntry> diaryCandidates = diaryRepository.findVisibleByFamilyAndTarget(
                familyId,
                targetUserId,
                viewerUserId,
                diaryCandidateLimit);
        List<DiaryEntry> relatedDiaryCandidates = diaryRepository.findVisibleRelatedByFamilyAndTarget(
                familyId,
                targetUserId,
                viewerUserId,
                diaryCandidateLimit);
        diaryCandidates = mergeDiaryCandidates(diaryCandidates, relatedDiaryCandidates);
        List<MemoryEntry> memoryCandidates = memoryRepository.findActiveFamilyMemories(
                familyId,
                viewerUserId,
                memoryCandidateLimit);
        List<GrowthGuardRecord> growthCandidates = growthRecordRepository.findVisibleByFamily(
                familyId,
                viewerUserId,
                memoryCandidateLimit);
        attachSocialWeights(memoryCandidates, growthCandidates, viewerUserId);

        long readyEmbeddings = embeddingRepository.countReadyByFamilyId(familyId);
        VectorRanking vectorRanking = vectorRank(
                familyId,
                normalizedQuery,
                diaryCandidates,
                memoryCandidates,
                growthCandidates,
                diaryLimit,
                memoryLimit,
                readyEmbeddings);

        List<DiaryEntry> diaries = vectorRanking.usedVector()
                ? vectorRanking.diaries()
                : rankDiaries(diaryCandidates, normalizedQuery, diaryLimit);
        List<MemoryEntry> memories = vectorRanking.usedVector()
                ? vectorRanking.memories()
                : rankMemories(memoryCandidates, normalizedQuery, memoryLimit);
        List<GrowthGuardRecord> growthRecords = vectorRanking.usedVector()
                ? vectorRanking.growthRecords()
                : rankGrowthRecords(growthCandidates, normalizedQuery, memoryLimit);

        return AuthorizedMemoryRecallResult.builder()
                .diaries(diaries)
                .memories(memories)
                .growthRecords(growthRecords)
                .diaryCount(diaries.size())
                .memoryCount(memories.size())
                .growthRecordCount(growthRecords.size())
                .sources(buildSourceSummaries(diaries, memories, growthRecords))
                .query(normalizedQuery)
                .embeddingReadyCount(readyEmbeddings)
                .retrievalMode(vectorRanking.usedVector() ? "VECTOR_WITH_TEXT_FALLBACK" : "TEXT_FALLBACK")
                .build();
    }

    private static List<DiaryEntry> mergeDiaryCandidates(
            List<DiaryEntry> selfAuthored,
            List<DiaryEntry> relatedByFamily) {
        Map<Long, DiaryEntry> byId = new LinkedHashMap<>();
        for (DiaryEntry entry : selfAuthored) {
            if (entry != null && entry.getId() != null) {
                byId.put(entry.getId(), withMirrorSource(entry, "SELF_AUTHORED"));
            }
        }
        for (DiaryEntry entry : relatedByFamily) {
            if (entry != null && entry.getId() != null && !byId.containsKey(entry.getId())) {
                byId.put(entry.getId(), withMirrorSource(entry, "RELATED_BY_FAMILY"));
            }
        }
        return new ArrayList<>(byId.values());
    }

    @SuppressWarnings("unchecked")
    private static DiaryEntry withMirrorSource(DiaryEntry entry, String sourceType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (entry.getMetadata() instanceof Map<?, ?> map) {
            metadata.putAll((Map<String, Object>) map);
        }
        metadata.put("mirrorSourceType", sourceType);
        entry.setMetadata(metadata);
        return entry;
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
            return VectorRanking.empty();
        }

        try {
            Map<String, Object> response = aiServiceClient.embedText(Map.of(
                    "text", query,
                    "dimensions", 1536));
            if (!Boolean.TRUE.equals(response.get("success")) || !(response.get("embedding") instanceof List<?> values)) {
                return VectorRanking.empty();
            }
            String vector = toVectorLiteral(values);
            List<Long> diaryIds = vectorRankIds(familyId, "DIARY", diaryCandidates.stream().map(DiaryEntry::getId).toList(), vector, diaryLimit);
            List<Long> memoryIds = vectorRankIds(familyId, "MEMORY", memoryCandidates.stream().map(MemoryEntry::getId).toList(), vector, memoryLimit);
            List<Long> growthIds = vectorRankIds(familyId, "GROWTH_OBSERVATION", growthCandidates.stream().map(GrowthGuardRecord::getId).toList(), vector, memoryLimit);
            diaryIds = filterSupportedDiaryIds(diaryCandidates, diaryIds, query);
            memoryIds = filterSupportedMemoryIds(memoryCandidates, memoryIds, query);
            growthIds = filterSupportedGrowthIds(growthCandidates, growthIds, query);

            List<DiaryEntry> diaries = mergeVectorAndTextDiaries(diaryCandidates, diaryIds, query, diaryLimit);
            List<MemoryEntry> memories = mergeVectorAndTextMemories(memoryCandidates, memoryIds, query, memoryLimit);
            List<GrowthGuardRecord> growthRecords = mergeVectorAndTextGrowthRecords(growthCandidates, growthIds, query, memoryLimit);
            return new VectorRanking(diaries, memories, growthRecords, !diaryIds.isEmpty() || !memoryIds.isEmpty() || !growthIds.isEmpty());
        } catch (Exception e) {
            log.warn("Vector memory recall failed, using text fallback: {}", e.getMessage());
            return VectorRanking.empty();
        }
    }

    private List<Long> vectorRankIds(Long familyId, String sourceType, List<Long> sourceIds, String vector, int limit) {
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", sourceIds.stream().map(id -> "?").toList());
        List<Object> params = new ArrayList<>();
        params.add(familyId);
        params.add(sourceType);
        params.addAll(sourceIds);
        params.add(vector);
        params.add(MAX_VECTOR_DISTANCE);
        params.add(vector);
        params.add(limit);
        return jdbcTemplate.queryForList("""
                SELECT ranked.source_id
                FROM (
                    SELECT DISTINCT ON (source_id) source_id, embedding, updated_at
                    FROM memory_embeddings
                    WHERE family_id = ?
                      AND source_type = ?
                      AND source_id IN (%s)
                      AND status = 'READY'
                      AND embedding IS NOT NULL
                    ORDER BY source_id, updated_at DESC
                ) ranked
                WHERE ranked.embedding <=> ?::vector <= ?
                ORDER BY ranked.embedding <=> ?::vector
                LIMIT ?
                """.formatted(placeholders), Long.class, params.toArray());
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

    private static String toVectorLiteral(List<?> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Double.parseDouble(String.valueOf(values.get(i))));
        }
        return builder.append(']').toString();
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

    private void attachSocialWeights(
            List<MemoryEntry> memoryCandidates,
            List<GrowthGuardRecord> growthCandidates,
            Long viewerUserId) {
        memoryCandidates.forEach(entry -> attachVoteStats(entry, viewerUserId));
        growthCandidates.forEach(record -> attachStalenessStats(record, viewerUserId));
    }

    private void attachVoteStats(MemoryEntry entry, Long viewerUserId) {
        if (entry == null || entry.getId() == null) {
            return;
        }
        MemoryVoteStats stats = memoryVoteRepository.statsByMemoryId(entry.getId(), viewerUserId);
        if (stats == null) {
            stats = new MemoryVoteStats(entry.getId(), 0, 0, 0, 1.0, null);
        }
        Map<String, Object> metadata = mutableMetadata(entry.getMetadata());
        metadata.put("voteStats", Map.of(
                "memoryId", entry.getId(),
                "upVotes", stats.getUpVotes(),
                "downVotes", stats.getDownVotes(),
                "voteScore", stats.getVoteScore(),
                "consensusWeight", stats.getConsensusWeight(),
                "myVote", stats.getMyVote() == null ? "" : stats.getMyVote()));
        entry.setMetadata(metadata);
    }

    private void attachStalenessStats(GrowthGuardRecord record, Long viewerUserId) {
        if (record == null || record.getId() == null) {
            return;
        }
        GrowthStalenessStats stats = stalenessVoteRepository.statsByRecordId(record.getId(), viewerUserId);
        if (stats == null) {
            stats = new GrowthStalenessStats(record.getId(), 0, 1.0, false);
        }
        Map<String, Object> metadata = mutableMetadata(record.getMetadata());
        metadata.put("stalenessStats", Map.of(
                "recordId", record.getId(),
                "staleVotes", stats.getStaleVotes(),
                "stalenessWeight", stats.getStalenessWeight(),
                "myVoted", stats.isMyVoted()));
        record.setMetadata(metadata);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private static List<RecallSourceSummary> buildSourceSummaries(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords) {
        List<RecallSourceSummary> summaries = new ArrayList<>();
        for (DiaryEntry entry : diaries) {
            if (entry == null || entry.getId() == null) {
                continue;
            }
            Map<?, ?> index = metadataIndex(entry.getMetadata());
            summaries.add(RecallSourceSummary.builder()
                    .id("diary-" + entry.getId())
                    .sourceType("LIFE_RECORD")
                    .title(firstNonBlank(structuredText(entry.getStructured(), "title"), structuredText(entry.getStructured(), "entryType"), "每日记录"))
                    .snippet(snippet(entry.getRawText()))
                    .visibility(entry.getVisibility())
                    .temporalLayer(asString(index.get("temporalLayer")))
                    .topics(stringList(index.get("topics")))
                    .scenes(stringList(index.get("scenes")))
                    .build());
        }
        for (MemoryEntry entry : memories) {
            if (entry == null || entry.getId() == null) {
                continue;
            }
            Map<?, ?> index = metadataIndex(entry.getMetadata());
            summaries.add(RecallSourceSummary.builder()
                    .id("memory-" + entry.getId())
                    .sourceType("FAMILY_EXPERIENCE")
                    .title(firstNonBlank(entry.getSummary(), entry.getType(), "经验沉淀"))
                    .snippet(snippet(firstNonBlank(entry.getSummary(), entry.getContent(), "")))
                    .visibility(entry.getScope())
                    .temporalLayer(asString(index.get("temporalLayer")))
                    .topics(stringList(index.get("topics")))
                    .scenes(stringList(index.get("scenes")))
                    .build());
        }
        for (GrowthGuardRecord record : growthRecords) {
            if (record == null || record.getId() == null) {
                continue;
            }
            Map<?, ?> index = metadataIndex(record.getMetadata());
            summaries.add(RecallSourceSummary.builder()
                    .id("growth-" + record.getId())
                    .sourceType("GROWTH_OBSERVATION")
                    .title(firstNonBlank(record.getCategory(), "成长观察"))
                    .snippet(snippet(record.getContent()))
                    .visibility(record.getVisibility())
                    .temporalLayer(asString(index.get("temporalLayer")))
                    .topics(stringList(index.get("topics")))
                    .scenes(stringList(index.get("scenes")))
                    .build());
        }
        return summaries;
    }

    private static Map<?, ?> metadataIndex(Object metadata) {
        if (metadata instanceof Map<?, ?> map && map.get("index") instanceof Map<?, ?> index) {
            return index;
        }
        return Map.of();
    }

    private static String structuredText(Object structured, String key) {
        if (structured instanceof Map<?, ?> map) {
            return asString(map.get(key));
        }
        return "";
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(item -> !item.isBlank())
                .limit(6)
                .toList();
    }

    private static String snippet(String value) {
        String text = firstNonBlank(value, "")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() <= 90) {
            return text;
        }
        return text.substring(0, 90) + "...";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}，。！？；：“”‘’（）【】《》、]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean shouldRecallFamilyContext(String normalizedQuery, String scene) {
        if ("FAMILY_AGENT".equalsIgnoreCase(firstNonBlank(scene, ""))) {
            return true;
        }
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return true;
        }
        String compactQuery = normalizedQuery.replace(" ", "");
        return FAMILY_RELEVANCE_TERMS.stream().anyMatch(term ->
                normalizedQuery.contains(term) || compactQuery.contains(term));
    }

    private static AuthorizedMemoryRecallResult emptyRecall(String query, String retrievalMode) {
        return AuthorizedMemoryRecallResult.builder()
                .diaries(List.of())
                .memories(List.of())
                .growthRecords(List.of())
                .diaryCount(0)
                .memoryCount(0)
                .growthRecordCount(0)
                .sources(List.of())
                .retrievalMode(retrievalMode)
                .query(query)
                .embeddingReadyCount(0)
                .build();
    }

    private record VectorRanking(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords,
            boolean usedVector) {
        private static VectorRanking empty() {
            return new VectorRanking(List.of(), List.of(), List.of(), false);
        }
    }
}
