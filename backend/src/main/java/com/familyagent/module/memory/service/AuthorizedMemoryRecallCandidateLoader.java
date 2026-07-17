package com.familyagent.module.memory.service;

import com.familyagent.common.constant.DiaryRecallSource;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.MemoryRecallDiaryFacade;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.MemoryRecallGrowthFacade;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthorizedMemoryRecallCandidateLoader {

    private static final int CANDIDATE_MULTIPLIER = 5;

    private final MemoryRecallDiaryFacade diaryRecallFacade;
    private final MemoryEntryRepository memoryRepository;
    private final MemoryRecallGrowthFacade growthRecallFacade;
    private final AuthorizedMemoryRecallSocialSupport socialSupport;

    RecallCandidates loadFamily(Long familyId, Long viewerUserId, int diaryLimit, int memoryLimit) {
        int diaryCandidateLimit = candidateLimit(diaryLimit);
        int memoryCandidateLimit = candidateLimit(memoryLimit);
        List<DiaryEntry> diaries = diaryRecallFacade.findVisibleByFamily(
                familyId,
                viewerUserId,
                diaryCandidateLimit);
        List<MemoryEntry> memories = memoryRepository.findActiveFamilyMemories(
                familyId,
                viewerUserId,
                memoryCandidateLimit);
        List<GrowthGuardRecord> growthRecords = growthRecallFacade.findVisibleByFamily(
                familyId,
                viewerUserId,
                memoryCandidateLimit);
        socialSupport.attachSocialWeights(memories, growthRecords, viewerUserId);
        return new RecallCandidates(diaries, memories, growthRecords);
    }

    RecallCandidates loadMirror(
            Long familyId,
            Long targetUserId,
            Long viewerUserId,
            int diaryLimit,
            int memoryLimit) {
        int diaryCandidateLimit = candidateLimit(diaryLimit);
        int memoryCandidateLimit = candidateLimit(memoryLimit);
        List<DiaryEntry> selfAuthored = diaryRecallFacade.findVisibleByFamilyAndTarget(
                familyId,
                targetUserId,
                viewerUserId,
                diaryCandidateLimit);
        List<DiaryEntry> relatedByFamily = diaryRecallFacade.findVisibleRelatedByFamilyAndTarget(
                familyId,
                targetUserId,
                viewerUserId,
                diaryCandidateLimit);
        List<GrowthGuardRecord> growthRecords = growthRecallFacade.findVisibleByFamily(
                familyId,
                viewerUserId,
                memoryCandidateLimit);
        socialSupport.attachSocialWeights(List.of(), growthRecords, viewerUserId);
        return new RecallCandidates(
                mergeMirrorDiaries(selfAuthored, relatedByFamily),
                List.of(),
                growthRecords);
    }

    private static int candidateLimit(int resultLimit) {
        return Math.max(resultLimit * CANDIDATE_MULTIPLIER, resultLimit);
    }

    private static List<DiaryEntry> mergeMirrorDiaries(
            List<DiaryEntry> selfAuthored,
            List<DiaryEntry> relatedByFamily) {
        Map<Long, DiaryEntry> byId = new LinkedHashMap<>();
        addDiaries(byId, selfAuthored, DiaryRecallSource.SELF_AUTHORED, false);
        addDiaries(byId, relatedByFamily, DiaryRecallSource.RELATED_BY_FAMILY, true);
        return new ArrayList<>(byId.values());
    }

    private static void addDiaries(
            Map<Long, DiaryEntry> byId,
            List<DiaryEntry> entries,
            DiaryRecallSource source,
            boolean skipExisting) {
        for (DiaryEntry entry : entries) {
            if (entry == null
                    || entry.getId() == null
                    || (skipExisting && byId.containsKey(entry.getId()))) {
                continue;
            }
            byId.put(entry.getId(), withMirrorSource(entry, source));
        }
    }

    @SuppressWarnings("unchecked")
    private static DiaryEntry withMirrorSource(DiaryEntry entry, DiaryRecallSource source) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (entry.getMetadata() instanceof Map<?, ?> map) {
            metadata.putAll((Map<String, Object>) map);
        }
        metadata.put(DiaryRecallSource.METADATA_KEY, source.name());
        entry.setMetadata(metadata);
        return entry;
    }

    record RecallCandidates(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords) {}
}
