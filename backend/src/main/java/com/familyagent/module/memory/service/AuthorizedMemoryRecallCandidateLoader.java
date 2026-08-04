package com.familyagent.module.memory.service;

import com.familyagent.common.constant.DiaryRecallSource;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.repository.AuthorizedMemoryRecallRepository;
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

    private final AuthorizedMemoryRecallRepository recallRepository;
    private final AuthorizedMemoryRecallSocialSupport socialSupport;

    List<AuthorizedMemoryRecallCandidate> loadUnifiedFamily(
            Long familyId,
            Long viewerUserId,
            int candidateLimit) {
        List<AuthorizedMemoryRecallCandidate> candidates = candidates(
                recallRepository.findVisibleAuthorizedRecords(familyId, viewerUserId, candidateLimit));
        attachUnifiedSocialWeights(candidates, viewerUserId);
        return candidates;
    }

    List<AuthorizedMemoryRecallCandidate> loadUnifiedTarget(
            Long familyId,
            Long targetUserId,
            Long viewerUserId,
            int candidateLimit) {
        List<AuthorizedMemoryRecallCandidate> candidates = candidates(
                recallRepository.findVisibleAuthorizedRecordsForTarget(
                        familyId, targetUserId, viewerUserId, candidateLimit));
        attachUnifiedSocialWeights(candidates, viewerUserId);
        return candidates;
    }

    RecallCandidates loadFamily(Long familyId, Long viewerUserId, int diaryLimit, int memoryLimit) {
        int diaryCandidateLimit = candidateLimit(diaryLimit);
        int memoryCandidateLimit = candidateLimit(memoryLimit);
        List<AuthorizedMemoryRecallCandidate> diaries = candidates(recallRepository.findVisibleFamilyEntriesByOrigin(
                familyId,
                viewerUserId,
                MemoryOriginType.DIARY.name(),
                diaryCandidateLimit));
        List<AuthorizedMemoryRecallCandidate> memories = candidates(
                recallRepository.findVisibleCanonicalMemories(familyId, viewerUserId, memoryCandidateLimit));
        List<AuthorizedMemoryRecallCandidate> growthRecords = candidates(
                recallRepository.findVisibleFamilyEntriesByOrigin(
                familyId,
                viewerUserId,
                MemoryOriginType.GROWTH.name(),
                memoryCandidateLimit));
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
        List<AuthorizedMemoryRecallCandidate> selfAuthored = candidates(
                recallRepository.findVisibleMirrorSelfDiaries(
                familyId,
                targetUserId,
                viewerUserId,
                diaryCandidateLimit));
        List<AuthorizedMemoryRecallCandidate> relatedByFamily = candidates(
                recallRepository.findVisibleMirrorRelatedDiaries(
                familyId,
                targetUserId,
                viewerUserId,
                diaryCandidateLimit));
        List<AuthorizedMemoryRecallCandidate> growthRecords = candidates(
                recallRepository.findVisibleFamilyEntriesByOrigin(
                familyId,
                viewerUserId,
                MemoryOriginType.GROWTH.name(),
                memoryCandidateLimit));
        socialSupport.attachSocialWeights(List.of(), growthRecords, viewerUserId);
        return new RecallCandidates(
                mergeMirrorDiaries(selfAuthored, relatedByFamily),
                List.of(),
                growthRecords);
    }

    private static int candidateLimit(int resultLimit) {
        return Math.max(resultLimit * CANDIDATE_MULTIPLIER, resultLimit);
    }

    private static List<AuthorizedMemoryRecallCandidate> mergeMirrorDiaries(
            List<AuthorizedMemoryRecallCandidate> selfAuthored,
            List<AuthorizedMemoryRecallCandidate> relatedByFamily) {
        Map<Long, AuthorizedMemoryRecallCandidate> byId = new LinkedHashMap<>();
        addDiaries(byId, selfAuthored, DiaryRecallSource.SELF_AUTHORED, false);
        addDiaries(byId, relatedByFamily, DiaryRecallSource.RELATED_BY_FAMILY, true);
        return new ArrayList<>(byId.values());
    }

    private static void addDiaries(
            Map<Long, AuthorizedMemoryRecallCandidate> byId,
            List<AuthorizedMemoryRecallCandidate> entries,
            DiaryRecallSource source,
            boolean skipExisting) {
        for (AuthorizedMemoryRecallCandidate candidate : entries) {
            if (candidate == null
                    || candidate.entry().getId() == null
                    || (skipExisting && byId.containsKey(candidate.entry().getId()))) {
                continue;
            }
            byId.put(candidate.entry().getId(), candidate.withMirrorSource(source));
        }
    }

    private static List<AuthorizedMemoryRecallCandidate> candidates(
            List<com.familyagent.module.memory.entity.MemoryEntry> entries) {
        return entries.stream().map(AuthorizedMemoryRecallCandidate::from).toList();
    }

    private void attachUnifiedSocialWeights(
            List<AuthorizedMemoryRecallCandidate> candidates,
            Long viewerUserId) {
        List<AuthorizedMemoryRecallCandidate> memories = candidates.stream()
                .filter(candidate -> candidate.sourceType()
                        == com.familyagent.common.constant.MemoryRecallSourceType.FAMILY_EXPERIENCE)
                .toList();
        List<AuthorizedMemoryRecallCandidate> growth = candidates.stream()
                .filter(candidate -> candidate.sourceType()
                        == com.familyagent.common.constant.MemoryRecallSourceType.GROWTH_OBSERVATION)
                .toList();
        socialSupport.attachSocialWeights(memories, growth, viewerUserId);
    }

    record RecallCandidates(
            List<AuthorizedMemoryRecallCandidate> diaries,
            List<AuthorizedMemoryRecallCandidate> memories,
            List<AuthorizedMemoryRecallCandidate> growthRecords) {}
}
