package com.familyagent.module.diary.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MemoryRecallDiaryFacade {

    private final DiaryEntryRepository diaryRepository;

    public List<DiaryEntry> findVisibleByFamily(Long familyId, Long viewerUserId, int limit) {
        return diaryRepository.findVisibleByFamily(familyId, viewerUserId, limit);
    }

    public List<DiaryEntry> findVisibleByFamilyAndTarget(
            Long familyId,
            Long targetUserId,
            Long viewerUserId,
            int limit) {
        return diaryRepository.findVisibleByFamilyAndTarget(
                familyId,
                targetUserId,
                viewerUserId,
                limit);
    }

    public List<DiaryEntry> findVisibleRelatedByFamilyAndTarget(
            Long familyId,
            Long targetUserId,
            Long viewerUserId,
            int limit) {
        return diaryRepository.findVisibleRelatedByFamilyAndTarget(
                familyId,
                targetUserId,
                viewerUserId,
                limit);
    }
}
