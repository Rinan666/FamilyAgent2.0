package com.familyagent.module.memory.service;

import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.family.facade.FamilyRelationshipGraphFacade;
import com.familyagent.module.family.facade.FamilyRelationshipGraphView;
import com.familyagent.common.constant.MemoryRecallSourceType;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.repository.MemoryEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthorizedMemoryRecallService {

    private final AuthorizedMemoryRecallCandidateLoader candidateLoader;
    private final MemoryEmbeddingRepository embeddingRepository;
    private final FamilyMembershipFacade familyMembershipFacade;
    private final FamilyRelationshipGraphFacade relationshipGraphFacade;
    private final AuthorizedMemoryRecallRankingService rankingService;
    private final AuthorizedMemoryRecallSourceAssembler sourceAssembler;
    private final AuthorizedMemoryRecallQueryPolicy queryPolicy;
    private final AuthorizedMemoryRecallCompatibilityProjector compatibilityProjector;

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
        familyMembershipFacade.checkMembership(familyId);
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
        String normalizedQuery = queryPolicy.normalize(query);
        if (!queryPolicy.shouldRecallFamilyContext(normalizedQuery, scene)) {
            return emptyRecall(normalizedQuery, "SKIPPED_UNRELATED_QUERY");
        }

        AuthorizedMemoryRecallCandidateLoader.RecallCandidates candidates = candidateLoader.loadFamily(
                familyId,
                viewerUserId,
                diaryLimit,
                memoryLimit);
        return buildRecallResult(
                familyId,
                viewerUserId,
                null,
                normalizedQuery,
                diaryLimit,
                memoryLimit,
                candidates);
    }

    public AuthorizedMemoryRecallResult recallForMirror(
            Long familyId,
            Long targetUserId,
            Long viewerUserId,
            String query,
            int diaryLimit,
            int memoryLimit) {
        String normalizedQuery = queryPolicy.normalize(query);
        if (!queryPolicy.shouldRecallFamilyContext(normalizedQuery, null)) {
            return emptyRecall(normalizedQuery, "SKIPPED_UNRELATED_QUERY");
        }

        AuthorizedMemoryRecallCandidateLoader.RecallCandidates candidates = candidateLoader.loadMirror(
                familyId,
                targetUserId,
                viewerUserId,
                diaryLimit,
                memoryLimit);
        return buildRecallResult(
                familyId,
                viewerUserId,
                targetUserId,
                normalizedQuery,
                diaryLimit,
                memoryLimit,
                candidates);
    }

    private AuthorizedMemoryRecallResult buildRecallResult(
            Long familyId,
            Long viewerUserId,
            Long targetUserId,
            String normalizedQuery,
            int diaryLimit,
            int memoryLimit,
            AuthorizedMemoryRecallCandidateLoader.RecallCandidates candidates) {
        long readyEmbeddings = embeddingRepository.countReadyForRecall(
                familyId,
                candidates.memories().stream()
                        .filter(candidate -> candidate.sourceType() == MemoryRecallSourceType.PERSONAL_MEMORY)
                        .map(candidate -> candidate.entry().getId())
                        .toList());
        AuthorizedMemoryRecallRankingService.RankedRecall ranked = rankingService.rank(
                familyId,
                normalizedQuery,
                candidates.diaries(),
                candidates.memories(),
                candidates.growthRecords(),
                diaryLimit,
                memoryLimit,
                readyEmbeddings);
        FamilyRelationshipGraphView relationships = relationshipGraphFacade.resolve(
                familyId,
                viewerUserId,
                targetUserId,
                sourceAssembler.participantUserIds(
                        ranked.diaries(),
                        ranked.memories(),
                        ranked.growthRecords()));
        var diaries = compatibilityProjector.diaries(ranked.diaries());
        var memories = compatibilityProjector.memories(ranked.memories());
        var growthRecords = compatibilityProjector.growthRecords(ranked.growthRecords());
        return AuthorizedMemoryRecallResult.builder()
                .diaries(diaries)
                .memories(memories)
                .growthRecords(growthRecords)
                .diaryCount(diaries.size())
                .memoryCount(memories.size())
                .growthRecordCount(growthRecords.size())
                .sources(sourceAssembler.assemble(
                        ranked.diaries(),
                        ranked.memories(),
                        ranked.growthRecords(),
                        relationships))
                .query(normalizedQuery)
                .embeddingReadyCount(readyEmbeddings)
                .embeddingObservation(ranked.embeddingObservation())
                .retrievalMode(ranked.usedVector() ? "VECTOR_WITH_TEXT_FALLBACK" : "TEXT_FALLBACK")
                .build();
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
}
