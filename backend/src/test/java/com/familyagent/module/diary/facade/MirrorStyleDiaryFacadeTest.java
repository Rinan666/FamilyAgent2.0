package com.familyagent.module.diary.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MirrorStyleDiaryFacadeTest {

    @Test
    void shouldDelegatePrivateStyleQuery() {
        DiaryEntryRepository repository = mock(DiaryEntryRepository.class);
        DiaryEntry entry = new DiaryEntry();
        when(repository.findActiveByFamilyAndUserForStyle(10L, 201L, 80))
                .thenReturn(List.of(entry));
        MirrorStyleDiaryFacade facade = new MirrorStyleDiaryFacade(repository);

        assertEquals(List.of(entry), facade.findActiveByFamilyAndUser(10L, 201L, 80));
        verify(repository).findActiveByFamilyAndUserForStyle(10L, 201L, 80);
    }
}
