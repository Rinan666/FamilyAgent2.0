package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEmbeddingRepository;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthorizedMemoryRecallService {

    private static final int CANDIDATE_MULTIPLIER = 5;
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
    private final GrowthGuardRecordRepository growthRecordRepository;
    private final MemoryEmbeddingRepository embeddingRepository;
    private final FamilyService familyService;
    private final AuthorizedMemoryRecallSocialSupport socialSupport;
    private final AuthorizedMemoryRecallRankingService rankingService;

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

        RecallCandidates candidates = loadFamilyCandidates(familyId, viewerUserId, diaryLimit, memoryLimit);
        return buildRecallResult(familyId, normalizedQuery, diaryLimit, memoryLimit, candidates);
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

        RecallCandidates candidates = loadMirrorCandidates(familyId, targetUserId, viewerUserId, diaryLimit, memoryLimit);
        return buildRecallResult(familyId, normalizedQuery, diaryLimit, memoryLimit, candidates);
    }

    private RecallCandidates loadFamilyCandidates(Long familyId, Long viewerUserId, int diaryLimit, int memoryLimit) {
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
        socialSupport.attachSocialWeights(memoryCandidates, growthCandidates, viewerUserId);
        return new RecallCandidates(diaryCandidates, memoryCandidates, growthCandidates);
    }

    private RecallCandidates loadMirrorCandidates(
            Long familyId,
            Long targetUserId,
            Long viewerUserId,
            int diaryLimit,
            int memoryLimit) {
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
        socialSupport.attachSocialWeights(memoryCandidates, growthCandidates, viewerUserId);
        return new RecallCandidates(diaryCandidates, memoryCandidates, growthCandidates);
    }

    private AuthorizedMemoryRecallResult buildRecallResult(
            Long familyId,
            String normalizedQuery,
            int diaryLimit,
            int memoryLimit,
            RecallCandidates candidates) {
        long readyEmbeddings = embeddingRepository.countReadyByFamilyId(familyId);
        AuthorizedMemoryRecallRankingService.RankedRecall ranked = rankingService.rank(
                familyId,
                normalizedQuery,
                candidates.diaries(),
                candidates.memories(),
                candidates.growthRecords(),
                diaryLimit,
                memoryLimit,
                readyEmbeddings);
        return AuthorizedMemoryRecallResult.builder()
                .diaries(ranked.diaries())
                .memories(ranked.memories())
                .growthRecords(ranked.growthRecords())
                .diaryCount(ranked.diaries().size())
                .memoryCount(ranked.memories().size())
                .growthRecordCount(ranked.growthRecords().size())
                .sources(rankingService.buildSourceSummaries(
                        ranked.diaries(),
                        ranked.memories(),
                        ranked.growthRecords()))
                .query(normalizedQuery)
                .embeddingReadyCount(readyEmbeddings)
                .retrievalMode(ranked.usedVector() ? "VECTOR_WITH_TEXT_FALLBACK" : "TEXT_FALLBACK")
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

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}，。！？；：“”‘’（）【】《》]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean shouldRecallFamilyContext(String normalizedQuery, String scene) {
        if ("FAMILY_AGENT".equalsIgnoreCase(scene == null ? "" : scene.trim())) {
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

    private record RecallCandidates(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords) {}
}
