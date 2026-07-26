package com.familyagent.module.memory.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.UnifiedDiaryRecordRepository;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallCompatibilityProjector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UnifiedDiaryRecordFacade {

    private final UnifiedDiaryRecordRepository repository;
    private final AuthorizedMemoryRecallCompatibilityProjector projector;

    public DiaryEntry findById(Long diaryId) {
        return project(repository.findByOriginId(diaryId));
    }

    public List<DiaryEntry> findVisibleByFamily(Long familyId, Long viewerUserId, int limit) {
        return project(repository.findVisibleByFamily(familyId, viewerUserId, limit));
    }

    public long countVisibleByFamilySearch(
            Long familyId, Long viewerUserId, Long targetUserId, String keyword) {
        return repository.countVisibleByFamilySearch(familyId, viewerUserId, targetUserId, keyword);
    }

    public List<DiaryEntry> searchVisibleByFamily(
            Long familyId,
            Long viewerUserId,
            Long targetUserId,
            String keyword,
            int limit,
            long offset) {
        return project(repository.searchVisibleByFamily(
                familyId, viewerUserId, targetUserId, keyword, limit, offset));
    }

    public List<DiaryEntry> findSameDayMergeCandidates(
            Long familyId, Long userId, String visibility, String diaryDate) {
        return project(repository.findSameDayMergeCandidates(familyId, userId, visibility, diaryDate));
    }

    public int countTodayByUser(Long userId) {
        return repository.countTodayByUser(userId);
    }

    private DiaryEntry project(MemoryEntry entry) {
        return entry == null ? null : projector.diary(AuthorizedMemoryRecallCandidate.from(entry));
    }

    private List<DiaryEntry> project(List<MemoryEntry> entries) {
        return projector.diaries(entries.stream().map(AuthorizedMemoryRecallCandidate::from).toList());
    }
}
