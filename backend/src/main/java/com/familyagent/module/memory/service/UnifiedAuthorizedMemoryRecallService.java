package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryLibraryKind;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.family.facade.FamilyRelationshipGraphFacade;
import com.familyagent.module.family.facade.FamilyRelationshipGraphView;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.dto.MemoryRecallPlan;
import com.familyagent.module.memory.dto.UnifiedAuthorizedMemoryRecallResult;
import com.familyagent.module.memory.repository.MemoryEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UnifiedAuthorizedMemoryRecallService {

    private final AuthorizedMemoryRecallCandidateLoader candidateLoader;
    private final MemoryEmbeddingRepository embeddingRepository;
    private final FamilyMembershipFacade membershipFacade;
    private final FamilyRelationshipGraphFacade relationshipGraphFacade;
    private final AuthorizedMemoryRecallRankingService rankingService;
    private final AuthorizedMemoryRecallSourceAssembler sourceAssembler;

    public UnifiedAuthorizedMemoryRecallResult recall(
            Long familyId,
            Long viewerUserId,
            Long targetUserId,
            MemoryRecallPlan plan) {
        membershipFacade.checkMembership(familyId);
        if (plan == null || !plan.enabled()) {
            return empty(plan);
        }
        List<AuthorizedMemoryRecallCandidate> candidates = targetUserId == null
                ? candidateLoader.loadUnifiedFamily(familyId, viewerUserId, plan.candidateLimit())
                : candidateLoader.loadUnifiedTarget(
                        familyId, targetUserId, viewerUserId, plan.candidateLimit());
        long readyEmbeddings = embeddingRepository.countReadyForRecall(
                familyId,
                candidates.stream()
                        .filter(candidate -> MemoryLibraryKind.PERSONAL.name()
                                .equals(candidate.entry().getLibraryKind()))
                        .map(candidate -> candidate.entry().getId())
                        .toList());

        Map<Long, AuthorizedMemoryRecallCandidate> merged = new LinkedHashMap<>();
        boolean usedVector = false;
        EmbeddingCallObservation observation = null;
        for (String query : plan.queries()) {
            AuthorizedMemoryRecallRankingService.RankedUnifiedRecall ranked = rankingService.rankUnified(
                    familyId,
                    query,
                    candidates,
                    plan.resultLimit(),
                    readyEmbeddings);
            usedVector = usedVector || ranked.usedVector();
            if (observation == null || observation.success()) {
                observation = ranked.embeddingObservation();
            }
            for (AuthorizedMemoryRecallCandidate item : ranked.items()) {
                merged.putIfAbsent(item.entry().getId(), item);
                if (merged.size() >= plan.resultLimit()) {
                    break;
                }
            }
            if (merged.size() >= plan.resultLimit()) {
                break;
            }
        }

        List<AuthorizedMemoryRecallCandidate> items = selectDiverse(
                orderByPlan(new ArrayList<>(merged.values()), plan),
                plan.resultLimit());
        FamilyRelationshipGraphView relationships = relationshipGraphFacade.resolve(
                familyId,
                viewerUserId,
                targetUserId,
                sourceAssembler.participantUserIds(items));
        return new UnifiedAuthorizedMemoryRecallResult(
                items,
                sourceAssembler.assemble(items, relationships),
                usedVector ? "UNIFIED_VECTOR_WITH_TEXT_FALLBACK" : "UNIFIED_TEXT_FALLBACK",
                plan.queries(),
                readyEmbeddings,
                observation);
    }

    private static UnifiedAuthorizedMemoryRecallResult empty(MemoryRecallPlan plan) {
        return new UnifiedAuthorizedMemoryRecallResult(
                List.of(),
                List.of(),
                "SKIPPED_BY_INTENT",
                plan == null ? List.of() : plan.queries(),
                0,
                null);
    }

    private static List<AuthorizedMemoryRecallCandidate> orderByPlan(
            List<AuthorizedMemoryRecallCandidate> items,
            MemoryRecallPlan plan) {
        Map<Long, Integer> originalOrder = new LinkedHashMap<>();
        for (int index = 0; index < items.size(); index++) {
            originalOrder.put(items.get(index).entry().getId(), index);
        }
        items.sort(Comparator
                .comparingInt((AuthorizedMemoryRecallCandidate candidate) -> preferenceScore(candidate, plan))
                .reversed()
                .thenComparingInt(candidate -> originalOrder.getOrDefault(candidate.entry().getId(), Integer.MAX_VALUE)));
        return items;
    }

    private static int preferenceScore(
            AuthorizedMemoryRecallCandidate candidate,
            MemoryRecallPlan plan) {
        int score = 0;
        if (plan.preferredSubjectUserId() != null
                && plan.preferredSubjectUserId().equals(candidate.subjectUserId())) {
            score += 4;
        }
        if (candidate.entry().getType() != null
                && plan.preferredTypes().stream().anyMatch(type -> type.name().equals(candidate.entry().getType()))) {
            score += 2;
        }
        return score;
    }

    private static List<AuthorizedMemoryRecallCandidate> selectDiverse(
            List<AuthorizedMemoryRecallCandidate> ranked,
            int limit) {
        List<AuthorizedMemoryRecallCandidate> selected = new ArrayList<>();
        Set<Object> sourceTypes = new HashSet<>();
        for (AuthorizedMemoryRecallCandidate candidate : ranked) {
            if (sourceTypes.add(candidate.sourceType())) {
                selected.add(candidate);
            }
            if (selected.size() >= limit) {
                return selected;
            }
        }
        for (AuthorizedMemoryRecallCandidate candidate : ranked) {
            if (!selected.contains(candidate)) {
                selected.add(candidate);
            }
            if (selected.size() >= limit) {
                break;
            }
        }
        return selected;
    }
}
