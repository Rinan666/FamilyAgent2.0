package com.familyagent.module.diary.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryRecallDiaryFacadeTest {

    @Mock private DiaryEntryRepository diaryRepository;

    @Test
    void shouldDelegateAuthorizedRecallQueries() {
        DiaryEntry entry = new DiaryEntry();
        entry.setId(44L);
        when(diaryRepository.findVisibleByFamily(11L, 34L, 15)).thenReturn(List.of(entry));
        when(diaryRepository.findVisibleByFamilyAndTarget(11L, 35L, 34L, 15))
                .thenReturn(List.of(entry));
        when(diaryRepository.findVisibleRelatedByFamilyAndTarget(11L, 35L, 34L, 15))
                .thenReturn(List.of(entry));
        MemoryRecallDiaryFacade facade = new MemoryRecallDiaryFacade(diaryRepository);

        assertEquals(List.of(entry), facade.findVisibleByFamily(11L, 34L, 15));
        assertEquals(List.of(entry), facade.findVisibleByFamilyAndTarget(11L, 35L, 34L, 15));
        assertEquals(List.of(entry), facade.findVisibleRelatedByFamilyAndTarget(11L, 35L, 34L, 15));
        verify(diaryRepository).findVisibleByFamily(11L, 34L, 15);
        verify(diaryRepository).findVisibleByFamilyAndTarget(11L, 35L, 34L, 15);
        verify(diaryRepository).findVisibleRelatedByFamilyAndTarget(11L, 35L, 34L, 15);
    }
}
