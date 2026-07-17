package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.facade.MemoryLibraryFamilyFacade;
import com.familyagent.module.growth.dto.GrowthStalenessStats;
import com.familyagent.module.growth.facade.MemoryLibraryGrowthStalenessFacade;
import com.familyagent.module.memory.dto.MemoryVoteStats;
import com.familyagent.module.memory.facade.MemoryLibraryVoteFacade;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 记忆库统一 JDBC 查询，从 MemoryLibraryService 拆出。
 */
@Component
@RequiredArgsConstructor
public class MemoryLibraryQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 48;
    private static final Set<String> TYPES = Set.of(
            "ALL", "LIFE_RECORD", "FAMILY_EXPERIENCE", "GROWTH_OBSERVATION", "AI_SUMMARY");

    private final MemoryLibraryQueryGateway queryGateway;
    private final MemoryLibraryFamilyFacade familyService;
    private final MemoryLibraryVoteFacade memoryVoteFacade;
    private final MemoryLibraryGrowthStalenessFacade growthStalenessFacade;

    public PageResult<MemoryLibraryItem> search(MemoryLibrarySearchRequest request) {
        return query(request, false);
    }

    public PageResult<MemoryLibraryItem> archived(MemoryLibrarySearchRequest request) {
        return query(request, true);
    }

    PageResult<MemoryLibraryItem> query(MemoryLibrarySearchRequest request, boolean archived) {
        if (request.getFamilyId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        }
        familyService.checkMembership(request.getFamilyId());
        Long viewerUserId = CurrentUserGuard.currentUserId();
        String type = normalizeType(request.getType());
        String keyword = MemoryLibrarySupport.blankToNull(request.getKeyword());
        List<String> searchTerms = MemoryLibrarySupport.searchTerms(keyword);
        Long memberUserId = request.getMemberUserId();
        String visibility = MemoryLibrarySupport.blankToNull(request.getVisibility());
        String tag = MemoryLibrarySupport.blankToNull(request.getTag());
        LocalDate dateFrom = request.getDateFrom();
        LocalDate dateTo = request.getDateTo();
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());
        int offset = (page - 1) * pageSize;

        MemoryLibraryQueryGateway.QueryResult result = queryGateway.query(
                new MemoryLibraryQueryGateway.QueryCriteria(
                        request.getFamilyId(),
                        viewerUserId,
                        searchTerms,
                        type,
                        memberUserId,
                        visibility,
                        tag,
                        dateFrom,
                        dateTo,
                        archived,
                        pageSize,
                        offset));
        List<MemoryLibraryItem> items = result.items();
        items.forEach(item -> attachDynamicSignals(item, viewerUserId));
        return PageResult.of(items, page, pageSize, result.total());
    }

    private void attachDynamicSignals(MemoryLibraryItem item, Long viewerUserId) {
        if (item == null || item.getId() == null || item.getId().isBlank()) return;
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(item.getId());
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(item.getMetadata());
        if ("memory".equals(parsed.prefix()) && "FAMILY_EXPERIENCE".equals(item.getSourceType())) {
            MemoryVoteStats stats = memoryVoteFacade.getStats(parsed.id(), viewerUserId);
            if (stats == null) stats = new MemoryVoteStats(parsed.id(), 0, 0, 0, 1.0, null);
            metadata.put("voteStats", Map.of(
                    "memoryId", parsed.id(),
                    "upVotes", stats.getUpVotes(),
                    "downVotes", stats.getDownVotes(),
                    "voteScore", stats.getVoteScore(),
                    "consensusWeight", stats.getConsensusWeight(),
                    "myVote", stats.getMyVote() == null ? "" : stats.getMyVote()));
        }
        if ("growth".equals(parsed.prefix()) && "GROWTH_OBSERVATION".equals(item.getSourceType())) {
            GrowthStalenessStats stats = growthStalenessFacade.getStats(parsed.id(), viewerUserId);
            if (stats == null) stats = new GrowthStalenessStats(parsed.id(), 0, 1.0, false);
            metadata.put("stalenessStats", Map.of(
                    "recordId", parsed.id(),
                    "staleVotes", stats.getStaleVotes(),
                    "stalenessWeight", stats.getStalenessWeight(),
                    "myVoted", stats.isMyVoted()));
        }
        item.setMetadata(metadata);
    }

    private static int normalizePage(Integer page) {
        return page == null || page <= 0 ? DEFAULT_PAGE : page;
    }

    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    static String normalizeType(String type) {
        String normalized = type == null || type.isBlank() ? "ALL" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) throw new BusinessException(ErrorCode.BAD_REQUEST, "记忆库类型不支持");
        return normalized;
    }

}
