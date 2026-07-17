package com.familyagent.module.memory.service;

import com.familyagent.common.constant.DiaryRecallSource;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.MemoryRecallDiaryFacade;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.MemoryRecallGrowthFacade;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizedMemoryRecallCandidateLoaderTest {

    @Mock private MemoryRecallDiaryFacade diaryRecallFacade;
    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private MemoryRecallGrowthFacade growthRecallFacade;
    @Mock private AuthorizedMemoryRecallSocialSupport socialSupport;
    @InjectMocks private AuthorizedMemoryRecallCandidateLoader candidateLoader;

    @Test
    void loadFamily_usesExpandedLimitsAndAttachesSocialWeights() {
        DiaryEntry diary = diary(1L);
        MemoryEntry memory = memory(2L);
        GrowthGuardRecord growthRecord = growth(3L);
        when(diaryRecallFacade.findVisibleByFamily(10L, 101L, 15)).thenReturn(List.of(diary));
        when(memoryRepository.findActiveFamilyMemories(10L, 101L, 10)).thenReturn(List.of(memory));
        when(growthRecallFacade.findVisibleByFamily(10L, 101L, 10)).thenReturn(List.of(growthRecord));

        AuthorizedMemoryRecallCandidateLoader.RecallCandidates candidates =
                candidateLoader.loadFamily(10L, 101L, 3, 2);

        assertEquals(List.of(diary), candidates.diaries());
        assertEquals(List.of(memory), candidates.memories());
        assertEquals(List.of(growthRecord), candidates.growthRecords());
        verify(socialSupport).attachSocialWeights(List.of(memory), List.of(growthRecord), 101L);
    }

    @Test
    void loadMirror_prefersSelfAuthoredDuplicatesAndMarksDiarySources() {
        DiaryEntry selfAuthored = diary(11L);
        DiaryEntry duplicateRelated = diary(11L);
        DiaryEntry related = diary(12L);
        related.setMetadata(Map.of("existing", true));
        GrowthGuardRecord growthRecord = growth(13L);
        when(diaryRecallFacade.findVisibleByFamilyAndTarget(10L, 201L, 101L, 25))
                .thenReturn(List.of(selfAuthored));
        when(diaryRecallFacade.findVisibleRelatedByFamilyAndTarget(10L, 201L, 101L, 25))
                .thenReturn(List.of(duplicateRelated, related));
        when(growthRecallFacade.findVisibleByFamily(10L, 101L, 25))
                .thenReturn(List.of(growthRecord));

        AuthorizedMemoryRecallCandidateLoader.RecallCandidates candidates =
                candidateLoader.loadMirror(10L, 201L, 101L, 5, 5);

        assertEquals(List.of(selfAuthored, related), candidates.diaries());
        assertEquals(List.of(), candidates.memories());
        assertEquals(
                DiaryRecallSource.SELF_AUTHORED.name(),
                metadata(selfAuthored).get(DiaryRecallSource.METADATA_KEY));
        assertEquals(
                DiaryRecallSource.RELATED_BY_FAMILY.name(),
                metadata(related).get(DiaryRecallSource.METADATA_KEY));
        assertTrue((Boolean) metadata(related).get("existing"));
        verify(socialSupport).attachSocialWeights(List.of(), List.of(growthRecord), 101L);
    }

    private static DiaryEntry diary(Long id) {
        DiaryEntry entry = new DiaryEntry();
        entry.setId(id);
        return entry;
    }

    private static MemoryEntry memory(Long id) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        return entry;
    }

    private static GrowthGuardRecord growth(Long id) {
        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setId(id);
        return record;
    }

    private static Map<?, ?> metadata(DiaryEntry entry) {
        return (Map<?, ?>) entry.getMetadata();
    }
}
