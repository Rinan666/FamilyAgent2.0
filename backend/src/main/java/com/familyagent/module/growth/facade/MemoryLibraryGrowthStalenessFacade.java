package com.familyagent.module.growth.facade;

import com.familyagent.module.growth.dto.GrowthStalenessStats;
import com.familyagent.module.growth.repository.GrowthGuardStalenessVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryLibraryGrowthStalenessFacade {

    private final GrowthGuardStalenessVoteRepository voteRepository;

    public GrowthStalenessStats getStats(Long recordId, Long viewerUserId) {
        return voteRepository.statsByRecordId(recordId, viewerUserId);
    }
}
