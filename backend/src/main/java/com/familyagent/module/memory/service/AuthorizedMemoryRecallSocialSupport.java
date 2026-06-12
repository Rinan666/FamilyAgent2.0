package com.familyagent.module.memory.service;

import com.familyagent.module.growth.dto.GrowthStalenessStats;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardStalenessVoteRepository;
import com.familyagent.module.memory.dto.MemoryVoteStats;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthorizedMemoryRecallSocialSupport {

    private final MemoryEntryVoteRepository memoryVoteRepository;
    private final GrowthGuardStalenessVoteRepository stalenessVoteRepository;

    public void attachSocialWeights(
            List<MemoryEntry> memoryCandidates,
            List<GrowthGuardRecord> growthCandidates,
            Long viewerUserId) {
        memoryCandidates.forEach(entry -> attachVoteStats(entry, viewerUserId));
        growthCandidates.forEach(record -> attachStalenessStats(record, viewerUserId));
    }

    private void attachVoteStats(MemoryEntry entry, Long viewerUserId) {
        if (entry == null || entry.getId() == null) {
            return;
        }
        MemoryVoteStats stats = memoryVoteRepository.statsByMemoryId(entry.getId(), viewerUserId);
        if (stats == null) {
            stats = new MemoryVoteStats(entry.getId(), 0, 0, 0, 1.0, null);
        }
        Map<String, Object> metadata = mutableMetadata(entry.getMetadata());
        metadata.put("voteStats", Map.of(
                "memoryId", entry.getId(),
                "upVotes", stats.getUpVotes(),
                "downVotes", stats.getDownVotes(),
                "voteScore", stats.getVoteScore(),
                "consensusWeight", stats.getConsensusWeight(),
                "myVote", stats.getMyVote() == null ? "" : stats.getMyVote()));
        entry.setMetadata(metadata);
    }

    private void attachStalenessStats(GrowthGuardRecord record, Long viewerUserId) {
        if (record == null || record.getId() == null) {
            return;
        }
        GrowthStalenessStats stats = stalenessVoteRepository.statsByRecordId(record.getId(), viewerUserId);
        if (stats == null) {
            stats = new GrowthStalenessStats(record.getId(), 0, 1.0, false);
        }
        Map<String, Object> metadata = mutableMetadata(record.getMetadata());
        metadata.put("stalenessStats", Map.of(
                "recordId", record.getId(),
                "staleVotes", stats.getStaleVotes(),
                "stalenessWeight", stats.getStalenessWeight(),
                "myVoted", stats.isMyVoted()));
        record.setMetadata(metadata);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }
}
