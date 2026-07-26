package com.familyagent.module.memory.service;

import com.familyagent.common.constant.DiaryRecallSource;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.AuthorizedMemoryRecallRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizedMemoryRecallCandidateLoaderTest {

    @Mock private AuthorizedMemoryRecallRepository recallRepository;
    @Mock private AuthorizedMemoryRecallSocialSupport socialSupport;
    @InjectMocks private AuthorizedMemoryRecallCandidateLoader candidateLoader;

    @Test
    void loadFamily_readsEveryCategoryFromUnifiedMemoryEntries() {
        MemoryEntry diary = entry(101L, 1L, MemoryOriginType.DIARY);
        MemoryEntry memory = entry(2L, null, null);
        MemoryEntry growth = entry(103L, 3L, MemoryOriginType.GROWTH);
        when(recallRepository.findVisibleFamilyEntriesByOrigin(10L, 101L, "DIARY", 15))
                .thenReturn(List.of(diary));
        when(recallRepository.findVisibleCanonicalMemories(10L, 101L, 10))
                .thenReturn(List.of(memory));
        when(recallRepository.findVisibleFamilyEntriesByOrigin(10L, 101L, "GROWTH", 10))
                .thenReturn(List.of(growth));

        AuthorizedMemoryRecallCandidateLoader.RecallCandidates candidates =
                candidateLoader.loadFamily(10L, 101L, 3, 2);

        assertEquals(1L, candidates.diaries().get(0).vectorSourceId());
        assertEquals(2L, candidates.memories().get(0).vectorSourceId());
        assertEquals(3L, candidates.growthRecords().get(0).vectorSourceId());
        verify(socialSupport).attachSocialWeights(anyList(), anyList(), org.mockito.ArgumentMatchers.eq(101L));
    }

    @Test
    void loadMirror_marksSelfAndRelatedDiarySourcesWithoutMutatingMetadata() {
        MemoryEntry self = entry(111L, 11L, MemoryOriginType.DIARY);
        MemoryEntry related = entry(112L, 12L, MemoryOriginType.DIARY);
        when(recallRepository.findVisibleMirrorSelfDiaries(10L, 201L, 101L, 25))
                .thenReturn(List.of(self));
        when(recallRepository.findVisibleMirrorRelatedDiaries(10L, 201L, 101L, 25))
                .thenReturn(List.of(related));
        when(recallRepository.findVisibleFamilyEntriesByOrigin(10L, 101L, "GROWTH", 25))
                .thenReturn(List.of());

        AuthorizedMemoryRecallCandidateLoader.RecallCandidates candidates =
                candidateLoader.loadMirror(10L, 201L, 101L, 5, 5);

        assertEquals(DiaryRecallSource.SELF_AUTHORED, candidates.diaries().get(0).mirrorSource());
        assertEquals(DiaryRecallSource.RELATED_BY_FAMILY, candidates.diaries().get(1).mirrorSource());
    }

    private static MemoryEntry entry(Long id, Long originId, MemoryOriginType originType) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setLibraryKind("FAMILY");
        entry.setOriginId(originId);
        entry.setOriginType(originType == null ? null : originType.name());
        return entry;
    }
}
