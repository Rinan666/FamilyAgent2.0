package com.familyagent.module.memory.service;

import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.dto.GrowthStalenessStats;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.repository.GrowthGuardStalenessVoteRepository;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.dto.MemoryVoteStats;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEmbeddingRepository;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizedMemoryRecallServiceTest {

    @Mock private DiaryEntryRepository diaryRepository;
    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private MemoryEntryVoteRepository memoryVoteRepository;
    @Mock private GrowthGuardRecordRepository growthRecordRepository;
    @Mock private GrowthGuardStalenessVoteRepository stalenessVoteRepository;
    @Mock private MemoryEmbeddingRepository embeddingRepository;
    @Mock private AIServiceClient aiServiceClient;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private FamilyService familyService;
    @InjectMocks private AuthorizedMemoryRecallService recallService;

    @Test
    void recallForFamily_usesOnlyPermissionFilteredCandidates() {
        Long familyId = 10L;
        Long viewerUserId = 101L;
        DiaryEntry visibleDiary = diary(1L, familyId, 201L, "孩子最近刷牙需要提醒", "CARE_VISIBLE");
        MemoryEntry visibleMemory = memory(2L, familyId, 202L, "爷爷的牙齿经验", "牙齿健康要早留意", "FAMILY_VISIBLE");
        GrowthGuardRecord visibleGrowth = growth(3L, familyId, 201L, "DENTAL", "最近刷牙敷衍，继续观察", "CARE_VISIBLE");

        when(diaryRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(visibleDiary));
        when(memoryRepository.findActiveFamilyMemories(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(visibleMemory));
        when(growthRecordRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(visibleGrowth));
        when(embeddingRepository.countReadyByFamilyId(familyId)).thenReturn(0L);

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
        verify(aiServiceClient, never()).embedText(org.mockito.ArgumentMatchers.any());

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
    void recallForFamily_usesFamilyVotesAsMemoryWeight() {
        Long familyId = 10L;
        Long viewerUserId = 101L;
        MemoryEntry normalMemory = memory(31L, familyId, 202L, "tooth care", "tooth care reminder", "FAMILY_VISIBLE");
        MemoryEntry trustedMemory = memory(32L, familyId, 203L, "tooth care", "tooth care reminder", "FAMILY_VISIBLE");

        when(diaryRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of());
        when(memoryRepository.findActiveFamilyMemories(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(normalMemory, trustedMemory));
        when(growthRecordRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of());
        when(memoryVoteRepository.statsByMemoryId(31L, viewerUserId))
                .thenReturn(new MemoryVoteStats(31L, 0, 0, 0, 1.0, null));
        when(memoryVoteRepository.statsByMemoryId(32L, viewerUserId))
                .thenReturn(new MemoryVoteStats(32L, 6, 0, 6, 3.0, "UP"));
        when(embeddingRepository.countReadyByFamilyId(familyId)).thenReturn(0L);

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "tooth",
                3,
                2);

        assertEquals(2, result.getMemories().size());
        assertEquals(32L, result.getMemories().get(0).getId());
        assertEquals("UP", ((java.util.Map<?, ?>) ((java.util.Map<?, ?>) trustedMemory.getMetadata()).get("voteStats")).get("myVote"));
    }

    @Test
    void recallForFamily_usesStalenessVotesAsGrowthWeight() {
        Long familyId = 10L;
        Long viewerUserId = 101L;
        GrowthGuardRecord freshRecord = growth(41L, familyId, 201L, "SCREEN_TIME", "screen time signal", "FAMILY_VISIBLE");
        GrowthGuardRecord staleRecord = growth(42L, familyId, 201L, "SCREEN_TIME", "screen time signal", "FAMILY_VISIBLE");
        freshRecord.setObservedAt(LocalDate.now());
        freshRecord.setCreatedAt(LocalDateTime.now());
        staleRecord.setObservedAt(LocalDate.now());
        staleRecord.setCreatedAt(LocalDateTime.now());

        when(diaryRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of());
        when(memoryRepository.findActiveFamilyMemories(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of());
        when(growthRecordRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(staleRecord, freshRecord));
        when(stalenessVoteRepository.statsByRecordId(41L, viewerUserId))
                .thenReturn(new GrowthStalenessStats(41L, 0, 1.0, false));
        when(stalenessVoteRepository.statsByRecordId(42L, viewerUserId))
                .thenReturn(new GrowthStalenessStats(42L, 4, 0.35, true));
        when(embeddingRepository.countReadyByFamilyId(familyId)).thenReturn(0L);

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "screen",
                3,
                2);

        assertEquals(2, result.getGrowthRecords().size());
        assertEquals(41L, result.getGrowthRecords().get(0).getId());
        assertEquals(true, ((java.util.Map<?, ?>) ((java.util.Map<?, ?>) staleRecord.getMetadata()).get("stalenessStats")).get("myVoted"));
    }

    @Test
    void recallForFamily_dropsVectorHitsWithoutTextOrIndexSupport() {
        Long familyId = 10L;
        Long viewerUserId = 101L;
        MemoryEntry unrelatedMemory = memory(21L, familyId, 202L, "做饭经验", "炖汤要小火慢熬", "FAMILY_VISIBLE");

        when(diaryRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of());
        when(memoryRepository.findActiveFamilyMemories(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of(unrelatedMemory));
        when(growthRecordRepository.findVisibleByFamily(eq(familyId), eq(viewerUserId), anyInt()))
                .thenReturn(List.of());
        when(embeddingRepository.countReadyByFamilyId(familyId)).thenReturn(1L);
        when(aiServiceClient.embedText(any())).thenReturn(Map.of(
                "success", true,
                "embedding", List.of(0.1, 0.2, 0.3)));
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(21L));

        AuthorizedMemoryRecallResult result = recallService.recallForFamily(
                familyId,
                viewerUserId,
                "牙齿 刷牙",
                3,
                3);

        assertEquals(List.of(), result.getMemories());
        assertEquals(0, result.getMemoryCount());
        assertEquals("TEXT_FALLBACK", result.getRetrievalMode());
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
