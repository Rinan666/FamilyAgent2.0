package com.familyagent.module.diary.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MirrorStyleDiaryFacade {

    private final DiaryEntryRepository diaryRepository;

    public List<DiaryEntry> findActiveByFamilyAndUser(Long familyId, Long userId, int limit) {
        return diaryRepository.findActiveByFamilyAndUserForStyle(familyId, userId, limit);
    }
}
