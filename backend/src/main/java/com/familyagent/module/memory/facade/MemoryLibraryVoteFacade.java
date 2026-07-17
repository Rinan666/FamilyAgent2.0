package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.MemoryVoteStats;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryLibraryVoteFacade {

    private final MemoryEntryVoteRepository voteRepository;

    public MemoryVoteStats getStats(Long memoryId, Long viewerUserId) {
        return voteRepository.statsByMemoryId(memoryId, viewerUserId);
    }
}
