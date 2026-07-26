package com.familyagent.module.memory.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallCompatibilityProjector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MirrorStyleMemoryFacade {

    private final MemoryEntryRepository memoryRepository;
    private final AuthorizedMemoryRecallCompatibilityProjector compatibilityProjector;

    public MirrorStyleRecords load(Long familyId, Long targetUserId, int limit) {
        List<AuthorizedMemoryRecallCandidate> diaries = candidates(
                memoryRepository.findActiveDiaryEntriesByAuthorForStyle(familyId, targetUserId, limit));
        List<MemoryEntry> memories = memoryRepository.findActiveCanonicalEntriesByAuthorForStyle(
                familyId,
                targetUserId,
                limit);
        List<AuthorizedMemoryRecallCandidate> growthRecords = candidates(
                memoryRepository.findActiveGrowthEntriesBySubjectForStyle(familyId, targetUserId, limit));
        return new MirrorStyleRecords(
                compatibilityProjector.diaries(diaries),
                memories,
                compatibilityProjector.growthRecords(growthRecords));
    }

    private static List<AuthorizedMemoryRecallCandidate> candidates(List<MemoryEntry> entries) {
        return entries.stream().map(AuthorizedMemoryRecallCandidate::from).toList();
    }

    public record MirrorStyleRecords(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords) {
    }
}
