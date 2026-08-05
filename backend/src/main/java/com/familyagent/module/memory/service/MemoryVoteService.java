package com.familyagent.module.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.memory.dto.MemoryVoteStats;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.entity.MemoryEntryVote;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Family-memory voting logic extracted from MemoryService.
 */
@Component
@RequiredArgsConstructor
public class MemoryVoteService {

    private static final Set<String> FAMILY_MEMORY_TYPES = MemoryContentType.names();

    private final MemoryEntryRepository memoryRepository;
    private final MemoryEntryVoteRepository voteRepository;
    private final FamilyMembershipFacade familyMembershipFacade;

    @Transactional
    public MemoryEntry vote(Long memoryId, String voteType) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        String normalizedVote = normalizeVoteType(voteType);
        MemoryEntry entry = memoryRepository.selectById(memoryId);
        if (entry == null || !EntityStatus.ACTIVE.name().equals(entry.getStatus()) || entry.getFamilyId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!FAMILY_MEMORY_TYPES.contains(entry.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有家庭记忆可以投票");
        }
        familyMembershipFacade.checkMembership(entry.getFamilyId());
        MemoryEntry visibleEntry = memoryRepository.findVisibleFamilyMemoryById(entry.getFamilyId(), memoryId, viewerUserId);
        if (visibleEntry == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权对不可见的家庭记忆投票");
        }

        MemoryEntryVote existing = voteRepository.selectOne(new LambdaQueryWrapper<MemoryEntryVote>()
                .eq(MemoryEntryVote::getMemoryId, memoryId)
                .eq(MemoryEntryVote::getUserId, viewerUserId)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = insertVoteIfMissing(memoryId, entry.getFamilyId(), viewerUserId, normalizedVote);
        } else if (!normalizedVote.equals(existing.getVoteType())) {
            existing.setVoteType(normalizedVote);
            voteRepository.updateById(existing);
        }

        attachVoteStats(visibleEntry, viewerUserId);
        return visibleEntry;
    }

    void attachVoteStats(MemoryEntry entry, Long viewerUserId) {
        MemoryVoteStats stats = voteRepository.statsByMemoryId(entry.getId(), viewerUserId);
        if (stats == null) {
            stats = new MemoryVoteStats(entry.getId(), 0, 0, 0, 1.0, null);
        }
        Map<String, Object> metadata = toMutableMap(entry.getMetadata());
        metadata.put("voteStats", Map.of(
                "memoryId", entry.getId(),
                "upVotes", stats.getUpVotes(),
                "downVotes", stats.getDownVotes(),
                "voteScore", stats.getVoteScore(),
                "consensusWeight", stats.getConsensusWeight(),
                "myVote", stats.getMyVote() == null ? "" : stats.getMyVote()));
        entry.setMetadata(metadata);
    }

    private MemoryEntryVote insertVoteIfMissing(Long memoryId, Long familyId, Long viewerUserId, String normalizedVote) {
        MemoryEntryVote vote = new MemoryEntryVote();
        vote.setMemoryId(memoryId);
        vote.setFamilyId(familyId);
        vote.setUserId(viewerUserId);
        vote.setVoteType(normalizedVote);
        try {
            voteRepository.insert(vote);
            return vote;
        } catch (DataIntegrityViolationException ex) {
            MemoryEntryVote existing = voteRepository.selectOne(new LambdaQueryWrapper<MemoryEntryVote>()
                    .eq(MemoryEntryVote::getMemoryId, memoryId)
                    .eq(MemoryEntryVote::getUserId, viewerUserId)
                    .last("LIMIT 1"));
            if (existing != null) {
                return existing;
            }
            throw ex;
        }
    }

    static MemoryVoteStats voteStatsFromMetadata(MemoryEntry entry) {
        if (entry.getMetadata() instanceof Map<?, ?> metadata
                && metadata.get("voteStats") instanceof Map<?, ?> stats) {
            int upVotes = asInt(stats.get("upVotes"));
            int downVotes = asInt(stats.get("downVotes"));
            int voteScore = asInt(stats.get("voteScore"));
            double consensusWeight = stats.get("consensusWeight") instanceof Number n ? n.doubleValue() : 1.0;
            Object myVote = stats.get("myVote");
            return new MemoryVoteStats(entry.getId(), upVotes, downVotes, voteScore, consensusWeight,
                    myVote == null ? null : String.valueOf(myVote));
        }
        return new MemoryVoteStats(entry.getId(), 0, 0, 0, 1.0, null);
    }

    private static String normalizeVoteType(String voteType) {
        String normalized = voteType == null ? "" : voteType.trim().toUpperCase(Locale.ROOT);
        if (!"UP".equals(normalized) && !"DOWN".equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "投票只能是 UP 或 DOWN");
        }
        return normalized;
    }

    private static int asInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMutableMap(Object metadata) {
        if (metadata instanceof Map<?, ?> map) return new HashMap<>((Map<String, Object>) map);
        return new HashMap<>();
    }
}
