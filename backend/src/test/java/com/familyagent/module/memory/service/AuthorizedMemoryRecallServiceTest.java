package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.dto.RecallSourceSummary;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEmbeddingRepository;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizedMemoryRecallServiceTest {

    @Mock private DiaryEntryRepository diaryRepository;
    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private GrowthGuardRecordRepository growthRecordRepository;
    @Mock private MemoryEmbeddingRepository embeddingRepository;
    @Mock private FamilyService familyService;
    @Mock private AuthorizedMemoryRecallSocialSupport socialSupport;
    @Mock private AuthorizedMemoryRecallRankingService rankingService;
    @InjectMocks private AuthorizedMemoryRecallService recallService;

    @Test
    void recallForFamily_skipsFamilyMemoryForUnrelatedQuery() {
        Long familyId = 10L;
        Long viewerUserId = 101L;

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "Python 装饰器怎么写",
                3,
                3);

        verify(familyService).checkMembership(familyId);
        verify(diaryRepository, never()).findVisibleByFamily(any(), any(), anyInt());
        verify(memoryRepository, never()).findActiveFamilyMemories(any(), any(), anyInt());
        verify(growthRecordRepository, never()).findVisibleByFamily(any(), any(), anyInt());
        verify(embeddingRepository, never()).countReadyByFamilyId(anyLong());
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
        DiaryEntry visibleDiary = diary(1L, familyId, 201L, "孩子最近刷牙需要提醒", "CARE_VISIBLE");
        MemoryEntry visibleMemory = memory(2L, familyId, 202L, "爷爷的护牙经验", "牙齿健康要及早留意", "FAMILY_VISIBLE");
        GrowthGuardRecord visibleGrowth = growth(3L, familyId, 201L, "DENTAL", "最近刷牙敷衍，继续观察", "CARE_VISIBLE");

        when(diaryRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(visibleDiary));
        when(memoryRepository.findActiveFamilyMemories(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(visibleMemory));
        when(growthRecordRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(visibleGrowth));
        when(embeddingRepository.countReadyByFamilyId(familyId)).thenReturn(0L);
        when(rankingService.rank(eq(familyId), eq("那我该怎么跟他说"), any(), any(), any(), eq(3), eq(3), eq(0L)))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(visibleDiary),
                        List.of(visibleMemory),
                        List.of(visibleGrowth),
                        false));
        when(rankingService.buildSourceSummaries(List.of(visibleDiary), List.of(visibleMemory), List.of(visibleGrowth)))
                .thenReturn(List.of());

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "那我该怎么跟他说？",
                "FAMILY_AGENT",
                3,
                3);

        verify(familyService).checkMembership(familyId);
        verify(diaryRepository).findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt());
        verify(memoryRepository).findActiveFamilyMemories(eq(familyId), eq(viewerUserId), anyInt());
        verify(growthRecordRepository).findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt());
        verify(socialSupport).attachSocialWeights(any(), any(), eq(viewerUserId));

        assertEquals("TEXT_FALLBACK", result.getRetrievalMode());
    }

    @Test
    void recallForFamily_usesOnlyPermissionFilteredCandidates() {
        Long familyId = 10L;
        Long viewerUserId = 101L;
        DiaryEntry visibleDiary = diary(1L, familyId, 201L, "孩子最近刷牙需要提醒", "CARE_VISIBLE");
        MemoryEntry visibleMemory = memory(2L, familyId, 202L, "爷爷的牙齿经验", "牙齿健康要早留意", "FAMILY_VISIBLE");
        GrowthGuardRecord visibleGrowth = growth(3L, familyId, 201L, "DENTAL", "最近刷牙敷衍，继续观察", "CARE_VISIBLE");
        List<RecallSourceSummary> summaries = List.of(
                RecallSourceSummary.builder().id("diary-1").build(),
                RecallSourceSummary.builder().id("memory-2").build(),
                RecallSourceSummary.builder().id("growth-3").build());

        when(diaryRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(visibleDiary));
        when(memoryRepository.findActiveFamilyMemories(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(visibleMemory));
        when(growthRecordRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(visibleGrowth));
        when(embeddingRepository.countReadyByFamilyId(familyId)).thenReturn(0L);
        when(rankingService.rank(eq(familyId), eq("牙齿 刷牙"), any(), any(), any(), eq(3), eq(3), eq(0L)))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(visibleDiary),
                        List.of(visibleMemory),
                        List.of(visibleGrowth),
                        false));
        when(rankingService.buildSourceSummaries(List.of(visibleDiary), List.of(visibleMemory), List.of(visibleGrowth)))
                .thenReturn(summaries);

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "牙齿 刷牙",
                3,
                3);

        verify(familyService).checkMembership(familyId);
        verify(diaryRepository).findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt());
        verify(memoryRepository).findActiveFamilyMemories(eq(familyId), eq(viewerUserId), anyInt());
        verify(growthRecordRepository).findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt());

        assertEquals(List.of(visibleDiary), result.getDiaries());
        assertEquals(List.of(visibleMemory), result.getMemories());
        assertEquals(List.of(visibleGrowth), result.getGrowthRecords());
        assertTrue(result.getSources().stream().allMatch(source ->
                source.getId().equals("diary-1")
                        || source.getId().equals("memory-2")
                        || source.getId().equals("growth-3")));
    }

    @Test
    void recallForMirror_limitsDiariesToTargetAndAuthorizedRelatedRecords() {
        Long familyId = 10L;
        Long targetUserId = 201L;
        Long viewerUserId = 101L;
        DiaryEntry targetDiary = diary(11L, familyId, targetUserId, "本人近期记录里提到志愿选择", "FAMILY_VISIBLE");
        DiaryEntry relatedDiary = diary(12L, familyId, 301L, "家人补充观察：志愿选择时比较谨慎", "FAMILY_VISIBLE");
        MemoryEntry familyExperience = memory(13L, familyId, 301L, "长辈选择经验", "选专业不要只看热门", "FAMILY_VISIBLE");

        when(diaryRepository.findVisibleByFamilyAndTarget(eq(familyId), eq(targetUserId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(targetDiary));
        when(diaryRepository.findVisibleRelatedByFamilyAndTarget(eq(familyId), eq(targetUserId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(relatedDiary));
        when(memoryRepository.findActiveFamilyMemories(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(familyExperience));
        when(growthRecordRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of());
        when(embeddingRepository.countReadyByFamilyId(familyId)).thenReturn(0L);
        when(rankingService.rank(eq(familyId), eq("志愿 专业"), any(), any(), any(), eq(5), eq(5), eq(0L)))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(targetDiary, relatedDiary),
                        List.of(familyExperience),
                        List.of(),
                        false));
        when(rankingService.buildSourceSummaries(List.of(targetDiary, relatedDiary), List.of(familyExperience), List.of()))
                .thenReturn(List.of());

        AuthorizedMemoryRecallResult result = recallService.recallForMirror(
                familyId,
                targetUserId,
                viewerUserId,
                "志愿 专业",
                5,
                5);

        verify(diaryRepository).findVisibleByFamilyAndTarget(eq(familyId), eq(targetUserId), eq(viewerUserId), anyInt());
        verify(diaryRepository).findVisibleRelatedByFamilyAndTarget(eq(familyId), eq(targetUserId), eq(viewerUserId), anyInt());
        verify(memoryRepository).findActiveFamilyMemories(eq(familyId), eq(viewerUserId), anyInt());

        assertEquals(2, result.getDiaries().size());
        assertTrue(result.getDiaries().stream().anyMatch(entry -> entry.getId().equals(11L)));
        assertTrue(result.getDiaries().stream().anyMatch(entry -> entry.getId().equals(12L)));
        assertEquals("SELF_AUTHORED", ((java.util.Map<?, ?>) targetDiary.getMetadata()).get("mirrorSourceType"));
        assertEquals("RELATED_BY_FAMILY", ((java.util.Map<?, ?>) relatedDiary.getMetadata()).get("mirrorSourceType"));
        assertEquals(List.of(familyExperience), result.getMemories());
    }

    @Test
    void recallForFamily_delegatesSocialWeightAttachmentBeforeRanking() {
        Long familyId = 10L;
        Long viewerUserId = 101L;
        MemoryEntry memory = memory(31L, familyId, 202L, "tooth care", "tooth care reminder", "FAMILY_VISIBLE");
        GrowthGuardRecord growth = growth(41L, familyId, 201L, "SCREEN_TIME", "screen time signal", "FAMILY_VISIBLE");

        when(diaryRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of());
        when(memoryRepository.findActiveFamilyMemories(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(memory));
        when(growthRecordRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(growth));
        when(embeddingRepository.countReadyByFamilyId(familyId)).thenReturn(0L);
        when(rankingService.rank(eq(familyId), eq("tooth"), any(), any(), any(), eq(3), eq(2), eq(0L)))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(),
                        List.of(memory),
                        List.of(growth),
                        false));
        when(rankingService.buildSourceSummaries(List.of(), List.of(memory), List.of(growth)))
                .thenReturn(List.of());

        recallService.recallForFamily(familyId, viewerUserId, "tooth", 3, 2);

        ArgumentCaptor<List<MemoryEntry>> memoryCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<GrowthGuardRecord>> growthCaptor = ArgumentCaptor.forClass(List.class);
        verify(socialSupport).attachSocialWeights(memoryCaptor.capture(), growthCaptor.capture(), eq(viewerUserId));
        assertEquals(List.of(memory), memoryCaptor.getValue());
        assertEquals(List.of(growth), growthCaptor.getValue());
    }

    @Test
    void recallForFamily_usesRankingModeToSetRetrievalMode() {
        Long familyId = 10L;
        Long viewerUserId = 101L;

        when(diaryRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of());
        when(memoryRepository.findActiveFamilyMemories(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of());
        when(growthRecordRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of());
        when(embeddingRepository.countReadyByFamilyId(familyId)).thenReturn(1L);
        when(rankingService.rank(eq(familyId), eq("牙齿 刷牙"), any(), any(), any(), eq(3), eq(3), eq(1L)))
                .thenReturn(new AuthorizedMemoryRecallRankingService.RankedRecall(
                        List.of(),
                        List.of(),
                        List.of(),
                        true));
        when(rankingService.buildSourceSummaries(List.of(), List.of(), List.of()))
                .thenReturn(List.of());

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "牙齿 刷牙",
                3,
                3);

        assertEquals("VECTOR_WITH_TEXT_FALLBACK", result.getRetrievalMode());
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
        entry.setStatus("ACTIVE");
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
        record.setStatus("ACTIVE");
        record.setSeverity(3);
        record.setObservedAt(LocalDate.now().minusDays(id));
        record.setCreatedAt(LocalDateTime.now().minusDays(id));
        return record;
    }
}
