package com.familyagent.module.memory.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.family.facade.FamilyRelationshipGraphFacade;
import com.familyagent.module.family.facade.FamilyRelationshipGraphView;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.dto.RecallSourceSummary;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEmbeddingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    @InjectMocks private AuthorizedMemoryRecallService recallService;

    private final FamilyRelationshipGraphView relationships = new FamilyRelationshipGraphView(Map.of());

    @BeforeEach
    void setUpRelationshipResolution() {
        lenient().when(sourceAssembler.participantUserIds(any(), any(), any())).thenReturn(Set.of());
        lenient().when(relationshipGraphFacade.resolve(anyLong(), anyLong(), nullable(Long.class), any()))
                .thenReturn(relationships);
    }

    @Test
    void recallForFamily_skipsFamilyMemoryForUnrelatedQuery() {
        Long familyId = 10L;
        Long viewerUserId = 101L;

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "Python decorator",
                3,
                3);

        verify(familyMembershipFacade).checkMembership(familyId);
        verify(candidateLoader, never()).loadFamily(any(), any(), anyInt(), anyInt());
        verify(embeddingRepository, never()).countReadyForRecall(anyLong(), any());
        verify(rankingService, never()).rank(any(), any(), any(), any(), any(), anyInt(), anyInt(), anyLong());

        assertEquals(List.of(), result.getDiaries());
        assertEquals(List.of(), result.getMemories());
        assertEquals(List.of(), result.getGrowthRecords());
        assertEquals("SKIPPED_UNRELATED_QUERY", result.getRetrievalMode());
        assertEquals(0, result.getMemoryCount());
    }

    @Test
    void recallForFamily_familyAgentSceneDoesNotShortCircuitPlainFollowUpQuestion() {
        Long familyId = 10L;
        Long viewerUserId = 101L;
        MemoryEntry visibleMemory = memory(
                2L,
                familyId,
                202L,
                "dental reminder",
                "Brush teeth before sleep",
                MemoryScope.FAMILY_VISIBLE.name());

        when(candidateLoader.loadFamily(familyId, viewerUserId, 3, 3))
                .thenReturn(candidates(List.of(), List.of(visibleMemory), List.of()));
        when(embeddingRepository.countReadyForRecall(eq(familyId), any())).thenReturn(0L);
        when(rankingService.rank(eq(familyId), eq("\u90a3\u6211\u8be5\u600e\u4e48\u8ddf\u4ed6\u804a"), any(), any(), any(), eq(3), eq(3), eq(0L)))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(),
                        List.of(visibleMemory),
                        List.of(),
                        false));
        when(sourceAssembler.assemble(List.of(), List.of(visibleMemory), List.of(), relationships))
                .thenReturn(List.of());

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "\u90a3\u6211\u8be5\u600e\u4e48\u8ddf\u4ed6\u804a\uff1f",
                "FAMILY_AGENT",
                3,
                3);

        verify(familyMembershipFacade).checkMembership(familyId);
        verify(candidateLoader).loadFamily(familyId, viewerUserId, 3, 3);

        assertEquals("TEXT_FALLBACK", result.getRetrievalMode());
    }

    @Test
    void recallForFamily_usesOnlyPermissionFilteredCandidates() {
        Long familyId = 10L;
        Long viewerUserId = 101L;
        MemoryEntry visibleMemory = memory(
                2L,
                familyId,
                202L,
                "tooth care note",
                "Remember to brush teeth after dinner",
                MemoryScope.FAMILY_VISIBLE.name());
        List<RecallSourceSummary> summaries = List.of(
                RecallSourceSummary.builder().id("memory-2").build());

        when(candidateLoader.loadFamily(familyId, viewerUserId, 3, 3))
                .thenReturn(candidates(List.of(), List.of(visibleMemory), List.of()));
        when(embeddingRepository.countReadyForRecall(eq(familyId), any())).thenReturn(0L);
        when(rankingService.rank(eq(familyId), eq("\u7259\u9f7f \u5237\u7259"), any(), any(), any(), eq(3), eq(3), eq(0L)))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(),
                        List.of(visibleMemory),
                        List.of(),
                        false));
        when(sourceAssembler.assemble(List.of(), List.of(visibleMemory), List.of(), relationships))
                .thenReturn(summaries);

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "\u7259\u9f7f \u5237\u7259",
                3,
                3);

        verify(familyMembershipFacade).checkMembership(familyId);
        verify(candidateLoader).loadFamily(familyId, viewerUserId, 3, 3);

        assertEquals(List.of(), result.getDiaries());
        assertEquals(List.of(visibleMemory), result.getMemories());
        assertEquals(List.of(), result.getGrowthRecords());
        assertTrue(result.getSources().stream().allMatch(source -> source.getId().equals("memory-2")));
    }

    @Test
    void recallForFamily_countsAuthorizedPersonalEmbeddingsWithoutTrustingFamilyId() {
        Long familyId = 10L;
        Long viewerUserId = 101L;
        MemoryEntry personalMemory = memory(
                7L,
                null,
                viewerUserId,
                "personal note",
                "A private idea recalled for its owner",
                "PRIVATE");
        personalMemory.setLibraryKind("PERSONAL");
        when(candidateLoader.loadFamily(familyId, viewerUserId, 3, 3))
                .thenReturn(candidates(List.of(), List.of(personalMemory), List.of()));
        when(embeddingRepository.countReadyForRecall(familyId, List.of(7L))).thenReturn(1L);
        when(rankingService.rank(eq(familyId), eq("personal idea"), any(), any(), any(), eq(3), eq(3), eq(1L)))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(), List.of(personalMemory), List.of(), false));
        when(sourceAssembler.assemble(List.of(), List.of(personalMemory), List.of(), relationships))
                .thenReturn(List.of());

        recallService.recallForFamily(familyId, viewerUserId, "personal idea", "FAMILY_AGENT", 3, 3);

        verify(embeddingRepository).countReadyForRecall(familyId, List.of(7L));
    }

    @Test
    void recallForFamily_loadsDiariesGrowthAndMemoriesForFamilyAgentContext() {
        Long familyId = 10L;
        Long viewerUserId = 101L;
        DiaryEntry visibleDiary = diary(
                21L,
                familyId,
                202L,
                "Recently we kept noting brushing teeth and bedtime routines",
                MemoryScope.FAMILY_VISIBLE.name());
        MemoryEntry visibleMemory = memory(
                22L,
                familyId,
                202L,
                "dental reminder",
                "Brush teeth before sleep",
                MemoryScope.FAMILY_VISIBLE.name());
        GrowthGuardRecord growthObservation = growth(
                23L,
                familyId,
                202L,
                "HEALTH",
                "Sleep has been late and brushing reminders need to be steadier",
                MemoryScope.FAMILY_VISIBLE.name());

        when(candidateLoader.loadFamily(familyId, viewerUserId, 3, 3))
                .thenReturn(candidates(
                        List.of(visibleDiary),
                        List.of(visibleMemory),
                        List.of(growthObservation)));
        when(embeddingRepository.countReadyForRecall(eq(familyId), any())).thenReturn(0L);
        when(rankingService.rank(eq(familyId), eq("\u7259\u9f7f \u7761\u7720"), any(), any(), any(), eq(3), eq(3), eq(0L)))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(visibleDiary),
                        List.of(visibleMemory),
                        List.of(growthObservation),
                        false));
        when(sourceAssembler.assemble(
                List.of(visibleDiary),
                List.of(visibleMemory),
                List.of(growthObservation),
                relationships))
                .thenReturn(List.of());

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "\u7259\u9f7f \u7761\u7720",
                "FAMILY_AGENT",
                3,
                3);

        verify(candidateLoader).loadFamily(familyId, viewerUserId, 3, 3);

        assertEquals(List.of(visibleDiary), result.getDiaries());
        assertEquals(List.of(visibleMemory), result.getMemories());
        assertEquals(List.of(growthObservation), result.getGrowthRecords());
    }

    @Test
    void recallForMirror_limitsDiariesToTargetAndAuthorizedRelatedRecords() {
        Long familyId = 10L;
        Long targetUserId = 201L;
        Long viewerUserId = 101L;
        DiaryEntry targetDiary = diary(
                11L,
                familyId,
                targetUserId,
                "The target recently wrote about college choice decisions",
                MemoryScope.FAMILY_VISIBLE.name());
        DiaryEntry relatedDiary = diary(
                12L,
                familyId,
                301L,
                "Family members added observations about how hard the choice felt",
                MemoryScope.FAMILY_VISIBLE.name());
        GrowthGuardRecord growthObservation = growth(
                13L,
                familyId,
                targetUserId,
                "COMMUNICATION",
                "Choice discussions trigger repeated comparison",
                MemoryScope.FAMILY_VISIBLE.name());

        when(candidateLoader.loadMirror(familyId, targetUserId, viewerUserId, 5, 5))
                .thenReturn(candidates(
                        List.of(targetDiary, relatedDiary),
                        List.of(),
                        List.of(growthObservation)));
        when(embeddingRepository.countReadyForRecall(eq(familyId), any())).thenReturn(0L);
        when(rankingService.rank(eq(familyId), eq("\u5fd7\u613f \u4e13\u4e1a"), any(), any(), any(), eq(5), eq(5), eq(0L)))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(targetDiary, relatedDiary),
                        List.of(),
                        List.of(growthObservation),
                        false));
        when(sourceAssembler.assemble(
                List.of(targetDiary, relatedDiary),
                List.of(),
                List.of(growthObservation),
                relationships))
                .thenReturn(List.of());

        AuthorizedMemoryRecallResult result = recallService.recallForMirror(
                familyId,
                targetUserId,
                viewerUserId,
                "\u5fd7\u613f \u4e13\u4e1a",
                5,
                5);

        verify(candidateLoader).loadMirror(familyId, targetUserId, viewerUserId, 5, 5);

        assertEquals(2, result.getDiaries().size());
        assertTrue(result.getDiaries().stream().anyMatch(entry -> entry.getId().equals(11L)));
        assertTrue(result.getDiaries().stream().anyMatch(entry -> entry.getId().equals(12L)));
        assertEquals(List.of(), result.getMemories());
        assertEquals(List.of(growthObservation), result.getGrowthRecords());
    }

    @Test
    void recallForFamily_usesRankingModeToSetRetrievalMode() {
        Long familyId = 10L;
        Long viewerUserId = 101L;
        EmbeddingCallObservation embeddingObservation = new EmbeddingCallObservation(
                true, true, false, "local", "local/hash-embedding", 1536, 18L, null);

        when(candidateLoader.loadFamily(familyId, viewerUserId, 3, 3))
                .thenReturn(candidates(List.of(), List.of(), List.of()));
        when(embeddingRepository.countReadyForRecall(eq(familyId), any())).thenReturn(1L);
        when(rankingService.rank(eq(familyId), eq("\u7259\u9f7f \u5237\u7259"), any(), any(), any(), eq(3), eq(3), eq(1L)))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(),
                        List.of(),
                        List.of(),
                        true,
                        embeddingObservation));
        when(sourceAssembler.assemble(List.of(), List.of(), List.of(), relationships))
                .thenReturn(List.of());

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "\u7259\u9f7f \u5237\u7259",
                3,
                3);

        assertEquals("VECTOR_WITH_TEXT_FALLBACK", result.getRetrievalMode());
        assertEquals(embeddingObservation, result.getEmbeddingObservation());
    }

    @Test
    void recallForFamily_detectsChineseFamilyQueryWithoutSceneOverride() {
        Long familyId = 10L;
        Long viewerUserId = 101L;
        MemoryEntry visibleMemory = memory(
                41L,
                familyId,
                202L,
                "sleep note",
                "Keep phone away before bed",
                MemoryScope.FAMILY_VISIBLE.name());

        when(candidateLoader.loadFamily(familyId, viewerUserId, 3, 3))
                .thenReturn(candidates(List.of(), List.of(visibleMemory), List.of()));
        when(embeddingRepository.countReadyForRecall(eq(familyId), any())).thenReturn(0L);
        when(rankingService.rank(eq(familyId), eq("\u7761\u7720 \u624b\u673a"), any(), any(), any(), eq(3), eq(3), eq(0L)))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(),
                        List.of(visibleMemory),
                        List.of(),
                        false));
        when(sourceAssembler.assemble(List.of(), List.of(visibleMemory), List.of(), relationships))
                .thenReturn(List.of());

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "\u7761\u7720\u3001\u624b\u673a",
                3,
                3);

        verify(candidateLoader).loadFamily(familyId, viewerUserId, 3, 3);
        assertEquals(List.of(visibleMemory), result.getMemories());
        assertEquals("\u7761\u7720 \u624b\u673a", result.getQuery());
    }

    @Test
    void recallForFamily_stillSkipsUnrelatedChineseTechnicalQuery() {
        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                10L,
                101L,
                "\u6570\u636e\u5e93\u7d22\u5f15\u4f18\u5316",
                3,
                3);

        verify(candidateLoader, never()).loadFamily(any(), any(), anyInt(), anyInt());
        assertEquals("SKIPPED_UNRELATED_QUERY", result.getRetrievalMode());
    }

    private static AuthorizedMemoryRecallCandidateLoader.RecallCandidates candidates(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords) {
        return new AuthorizedMemoryRecallCandidateLoader.RecallCandidates(diaries, memories, growthRecords);
    }

    private static DiaryEntry diary(Long id, Long familyId, Long userId, String rawText, String visibility) {
        DiaryEntry entry = new DiaryEntry();
        entry.setId(id);
        entry.setFamilyId(familyId);
        entry.setUserId(userId);
        entry.setRawText(rawText);
        entry.setVisibility(visibility);
        entry.setCreatedAt(LocalDateTime.now().minusDays(id));
        return entry;
    }

    private static MemoryEntry memory(Long id, Long familyId, Long userId, String summary, String content, String scope) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setFamilyId(familyId);
        entry.setUserId(userId);
        entry.setSummary(summary);
        entry.setContent(content);
        entry.setScope(scope);
        entry.setType("ELDER_ADVICE");
        entry.setStatus(EntityStatus.ACTIVE.name());
        entry.setImportance(3);
        entry.setCreatedAt(LocalDateTime.now().minusDays(id));
        entry.setUpdatedAt(LocalDateTime.now().minusDays(id));
        return entry;
    }

    private static GrowthGuardRecord growth(
            Long id,
            Long familyId,
            Long targetUserId,
            String category,
            String content,
            String visibility) {
        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setId(id);
        record.setFamilyId(familyId);
        record.setTargetUserId(targetUserId);
        record.setCreatedBy(101L);
        record.setCategory(category);
        record.setContent(content);
        record.setVisibility(visibility);
        record.setStatus(EntityStatus.ACTIVE.name());
        record.setSeverity(3);
        record.setObservedAt(LocalDate.now().minusDays(id));
        record.setCreatedAt(LocalDateTime.now().minusDays(id));
        return record;
    }
}
