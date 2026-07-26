package com.familyagent.module.memory.facade;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.UnifiedGrowthRecordRepository;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallCompatibilityProjector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UnifiedGrowthRecordFacade {

    private final UnifiedGrowthRecordRepository repository;
    private final AuthorizedMemoryRecallCompatibilityProjector projector;

    public GrowthGuardRecord findById(Long recordId) {
        return project(repository.findByOriginId(recordId));
    }

    public List<GrowthGuardRecord> findVisibleByFamily(Long familyId, Long viewerUserId, int limit) {
        return project(repository.findVisibleByFamily(familyId, viewerUserId, limit));
    }

    public long countVisibleByFamilySearch(
            Long familyId, Long viewerUserId, Long targetUserId, String keyword) {
        return repository.countVisibleByFamilySearch(familyId, viewerUserId, targetUserId, keyword);
    }

    public List<GrowthGuardRecord> searchVisibleByFamily(
            Long familyId,
            Long viewerUserId,
            Long targetUserId,
            String keyword,
            int limit,
            long offset) {
        return project(repository.searchVisibleByFamily(
                familyId, viewerUserId, targetUserId, keyword, limit, offset));
    }

    public int countTodayByUser(Long userId) {
        return repository.countTodayByUser(userId);
    }

    private GrowthGuardRecord project(MemoryEntry entry) {
        return entry == null ? null : projector.growthRecord(AuthorizedMemoryRecallCandidate.from(entry));
    }

    private List<GrowthGuardRecord> project(List<MemoryEntry> entries) {
        return projector.growthRecords(entries.stream().map(AuthorizedMemoryRecallCandidate::from).toList());
    }
}
