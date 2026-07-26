package com.familyagent.module.memory.service;

import com.familyagent.common.constant.DiaryRecallSource;
import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.family.facade.FamilyRelationshipGraphFacade;
import com.familyagent.module.family.facade.FamilyRelationshipGraphView;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizedMemoryRecallServiceTest {

    @Mock private AuthorizedMemoryRecallCandidateLoader candidateLoader;
    @Mock private MemoryEmbeddingRepository embeddingRepository;
    @Mock private FamilyMembershipFacade familyMembershipFacade;
    @Mock private FamilyRelationshipGraphFacade relationshipGraphFacade;
    @Mock private AuthorizedMemoryRecallRankingService rankingService;
    @Mock private AuthorizedMemoryRecallSourceAssembler sourceAssembler;
    @Spy private AuthorizedMemoryRecallQueryPolicy queryPolicy = new AuthorizedMemoryRecallQueryPolicy();
    @Spy private AuthorizedMemoryRecallCompatibilityProjector compatibilityProjector =
            new AuthorizedMemoryRecallCompatibilityProjector();
    @InjectMocks private AuthorizedMemoryRecallService recallService;

    private final FamilyRelationshipGraphView relationships = new FamilyRelationshipGraphView(Map.of());

    @BeforeEach
    void setUpRelationshipResolution() {
        lenient().when(sourceAssembler.participantUserIds(anyList(), anyList(), anyList())).thenReturn(Set.of());
        lenient().when(sourceAssembler.assemble(anyList(), anyList(), anyList(), any())).thenReturn(List.of());
        lenient().when(relationshipGraphFacade.resolve(anyLong(), anyLong(), nullable(Long.class), any()))
                .thenReturn(relationships);
    }

    @Test
    void recallForFamilySkipsUnrelatedQuery() {
        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                10L, 101L, "Python decorator", 3, 3);

        verify(familyMembershipFacade).checkMembership(10L);
        verify(candidateLoader, never()).loadFamily(any(), any(), anyInt(), anyInt());
        assertEquals("SKIPPED_UNRELATED_QUERY", result.getRetrievalMode());
        assertEquals(0, result.getMemoryCount());
    }

    @Test
    void recallForFamilyProjectsUnifiedEntriesToExistingContract() {
        AuthorizedMemoryRecallCandidate diary = candidate(121L, 21L, MemoryOriginType.DIARY, "FAMILY", 202L, null);
        AuthorizedMemoryRecallCandidate memory = candidate(22L, null, null, "FAMILY", 202L, null);
        AuthorizedMemoryRecallCandidate growth = candidate(123L, 23L, MemoryOriginType.GROWTH, "FAMILY", 101L, 303L);
        var candidates = candidates(List.of(diary), List.of(memory), List.of(growth));
        when(candidateLoader.loadFamily(10L, 101L, 3, 3)).thenReturn(candidates);
        when(embeddingRepository.countReadyForRecall(10L, List.of())).thenReturn(0L);
        when(rankingService.rank(10L, "牙齿 睡眠", candidates.diaries(), candidates.memories(),
                candidates.growthRecords(), 3, 3, 0L))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        candidates.diaries(), candidates.memories(), candidates.growthRecords(), false));

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                10L, 101L, "牙齿 睡眠", "FAMILY_AGENT", 3, 3);

        assertEquals(21L, result.getDiaries().get(0).getId());
        assertEquals(22L, result.getMemories().get(0).getId());
        assertEquals(23L, result.getGrowthRecords().get(0).getId());
        assertEquals(303L, result.getGrowthRecords().get(0).getTargetUserId());
    }

    @Test
    void recallForFamilyCountsAuthorizedPersonalEmbeddings() {
        AuthorizedMemoryRecallCandidate personal = candidate(7L, null, null, "PERSONAL", 101L, null);
        var candidates = candidates(List.of(), List.of(personal), List.of());
        when(candidateLoader.loadFamily(10L, 101L, 3, 3)).thenReturn(candidates);
        when(embeddingRepository.countReadyForRecall(10L, List.of(7L))).thenReturn(1L);
        when(rankingService.rank(10L, "personal idea", List.of(), List.of(personal), List.of(), 3, 3, 1L))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(), List.of(personal), List.of(), false));

        recallService.recallForFamily(10L, 101L, "personal idea", "FAMILY_AGENT", 3, 3);

        verify(embeddingRepository).countReadyForRecall(10L, List.of(7L));
    }

    @Test
    void recallForMirrorPreservesDiaryRelationshipSource() {
        AuthorizedMemoryRecallCandidate self = candidate(111L, 11L, MemoryOriginType.DIARY,
                "FAMILY", 201L, null).withMirrorSource(DiaryRecallSource.SELF_AUTHORED);
        AuthorizedMemoryRecallCandidate related = candidate(112L, 12L, MemoryOriginType.DIARY,
                "FAMILY", 301L, 201L).withMirrorSource(DiaryRecallSource.RELATED_BY_FAMILY);
        var candidates = candidates(List.of(self, related), List.of(), List.of());
        when(candidateLoader.loadMirror(10L, 201L, 101L, 5, 5)).thenReturn(candidates);
        when(embeddingRepository.countReadyForRecall(10L, List.of())).thenReturn(0L);
        when(rankingService.rank(10L, "志愿 专业", candidates.diaries(), List.of(), List.of(), 5, 5, 0L))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        candidates.diaries(), List.of(), List.of(), false));

        AuthorizedMemoryRecallResult result = recallService.recallForMirror(
                10L, 201L, 101L, "志愿 专业", 5, 5);

        assertEquals(2, result.getDiaries().size());
        assertTrue(result.getDiaries().stream().anyMatch(entry -> entry.getId().equals(11L)));
        assertEquals(DiaryRecallSource.RELATED_BY_FAMILY.name(),
                ((Map<?, ?>) result.getDiaries().get(1).getMetadata()).get(DiaryRecallSource.METADATA_KEY));
    }

    @Test
    void recallForFamilyUsesRankingModeAndObservation() {
        EmbeddingCallObservation observation = new EmbeddingCallObservation(
                true, true, false, "local", "local/hash-embedding", 1536, 18L, null);
        var candidates = candidates(List.of(), List.of(), List.of());
        when(candidateLoader.loadFamily(10L, 101L, 3, 3)).thenReturn(candidates);
        when(embeddingRepository.countReadyForRecall(10L, List.of())).thenReturn(1L);
        when(rankingService.rank(10L, "牙齿 刷牙", List.of(), List.of(), List.of(), 3, 3, 1L))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(), List.of(), List.of(), true, observation));

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(10L, 101L, "牙齿 刷牙", 3, 3);

        assertEquals("VECTOR_WITH_TEXT_FALLBACK", result.getRetrievalMode());
        assertEquals(observation, result.getEmbeddingObservation());
    }

    private static AuthorizedMemoryRecallCandidate candidate(
            Long id,
            Long originId,
            MemoryOriginType originType,
            String libraryKind,
            Long authorUserId,
            Long relatedUserId) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setFamilyId("PERSONAL".equals(libraryKind) ? null : 10L);
        entry.setLibraryKind(libraryKind);
        entry.setOriginId(originId);
        entry.setOriginType(originType == null ? null : originType.name());
        entry.setUserId(authorUserId);
        entry.setRelatedUserId(relatedUserId);
        entry.setTitle("title");
        entry.setContent("family memory content");
        entry.setSummary("summary");
        entry.setScope(MemoryScope.FAMILY_VISIBLE.name());
        entry.setStatus(EntityStatus.ACTIVE.name());
        entry.setImportance(3);
        entry.setOccurredAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        entry.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        entry.setUpdatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        return AuthorizedMemoryRecallCandidate.from(entry);
    }

    private static AuthorizedMemoryRecallCandidateLoader.RecallCandidates candidates(
            List<AuthorizedMemoryRecallCandidate> diaries,
            List<AuthorizedMemoryRecallCandidate> memories,
            List<AuthorizedMemoryRecallCandidate> growthRecords) {
        return new AuthorizedMemoryRecallCandidateLoader.RecallCandidates(diaries, memories, growthRecords);
    }
}
