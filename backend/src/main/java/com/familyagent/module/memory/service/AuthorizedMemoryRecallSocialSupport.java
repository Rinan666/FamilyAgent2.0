package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryRecallSourceType;
import com.familyagent.module.growth.dto.GrowthStalenessStats;
import com.familyagent.module.growth.facade.GrowthStalenessQueryFacade;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
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
    private final GrowthStalenessQueryFacade stalenessFacade;

    public void attachSocialWeights(
            List<AuthorizedMemoryRecallCandidate> memoryCandidates,
            List<AuthorizedMemoryRecallCandidate> growthCandidates,
            Long viewerUserId) {
        memoryCandidates.stream()
                .filter(candidate -> candidate.sourceType() == MemoryRecallSourceType.FAMILY_EXPERIENCE)
                .map(AuthorizedMemoryRecallCandidate::entry)
                .forEach(entry -> attachVoteStats(entry, viewerUserId));
        growthCandidates.forEach(candidate -> attachStalenessStats(candidate, viewerUserId));
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

    private void attachStalenessStats(AuthorizedMemoryRecallCandidate candidate, Long viewerUserId) {
        if (candidate == null || candidate.publicSourceId() == null) {
            return;
        }
        Long recordId = candidate.publicSourceId();
        GrowthStalenessStats stats = stalenessFacade.getStats(recordId, viewerUserId);
        if (stats == null) {
            stats = new GrowthStalenessStats(recordId, 0, 1.0, false);
        }
        Map<String, Object> metadata = mutableMetadata(candidate.entry().getMetadata());
        metadata.put("stalenessStats", Map.of(
                "recordId", recordId,
                "staleVotes", stats.getStaleVotes(),
                "stalenessWeight", stats.getStalenessWeight(),
                "myVoted", stats.isMyVoted()));
        candidate.entry().setMetadata(metadata);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }
}
