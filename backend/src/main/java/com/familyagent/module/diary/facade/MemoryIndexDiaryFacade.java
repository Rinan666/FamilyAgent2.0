package com.familyagent.module.diary.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MemoryIndexDiaryFacade {

    private final DiaryEntryRepository diaryRepository;

    public List<DiaryEntry> findActiveByFamily(Long familyId, int limit) {
        return diaryRepository.findActiveByFamilyForIndexing(familyId, limit);
    }

    public void update(DiaryEntry entry) {
        diaryRepository.updateById(entry);
    }
}
