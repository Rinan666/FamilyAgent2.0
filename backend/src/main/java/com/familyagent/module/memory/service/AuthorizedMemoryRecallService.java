package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEmbeddingRepository;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
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

    private final DiaryEntryRepository diaryRepository;
    private final MemoryEntryRepository memoryRepository;
    private final MemoryEmbeddingRepository embeddingRepository;
    private final AIServiceClient aiServiceClient;
    private final JdbcTemplate jdbcTemplate;

    public AuthorizedMemoryRecallResult recallForMirror(
            Long familyId,
            Long targetUserId,
            Long viewerUserId,
            String query,
            int diaryLimit,
            int memoryLimit) {
        String normalizedQuery = normalize(query);
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

        long readyEmbeddings = embeddingRepository.countReadyByFamilyId(familyId);
        VectorRanking vectorRanking = vectorRank(
                familyId,
                normalizedQuery,
                diaryCandidates,
                memoryCandidates,
                diaryLimit,
                memoryLimit,
                readyEmbeddings);

        List<DiaryEntry> diaries = vectorRanking.usedVector()
                ? vectorRanking.diaries()
                : rankDiaries(diaryCandidates, normalizedQuery, diaryLimit);
        List<MemoryEntry> memories = vectorRanking.usedVector()
                ? vectorRanking.memories()
                : rankMemories(memoryCandidates, normalizedQuery, memoryLimit);

        return AuthorizedMemoryRecallResult.builder()
                .diaries(diaries)
                .memories(memories)
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

            List<DiaryEntry> diaries = mergeVectorAndTextDiaries(diaryCandidates, diaryIds, query, diaryLimit);
            List<MemoryEntry> memories = mergeVectorAndTextMemories(memoryCandidates, memoryIds, query, memoryLimit);
            return new VectorRanking(diaries, memories, !diaryIds.isEmpty() || !memoryIds.isEmpty());
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
                ORDER BY ranked.embedding <=> ?::vector
                LIMIT ?
                """.formatted(placeholders), Long.class, params.toArray());
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
        return result.stream().limit(limit).toList();
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
        return result.stream().limit(limit).toList();
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
                .sorted(Comparator
                        .comparingInt((DiaryEntry entry) -> score(diarySearchText(entry), query)).reversed()
                        .thenComparing(DiaryEntry::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private static List<MemoryEntry> rankMemories(List<MemoryEntry> entries, String query, int limit) {
        return entries.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((MemoryEntry entry) -> score(memorySearchText(entry), query)).reversed()
                        .thenComparing(MemoryEntry::getImportance, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MemoryEntry::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
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
                .replaceAll("[\\p{Punct}，。！？；：“”‘’（）【】《》、]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record VectorRanking(List<DiaryEntry> diaries, List<MemoryEntry> memories, boolean usedVector) {
        private static VectorRanking empty() {
            return new VectorRanking(List.of(), List.of(), false);
        }
    }
}
