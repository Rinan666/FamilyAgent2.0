package com.familyagent.module.memory.service;

import com.familyagent.common.response.PageResult;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 家族经验列表查询与关键词搜索，从 MemoryService 拆出。
 */
@Component
@RequiredArgsConstructor
public class MemorySearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;
    private static final int DEFAULT_PAGE_SIZE = 6;
    private static final int MAX_PAGE_SIZE = 20;

    private final MemoryEntryRepository memoryRepository;
    private final FamilyService familyService;
    private final MemoryVoteService memoryVoteService;

    public List<MemoryEntry> listFamilyMemories(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        List<MemoryEntry> entries = memoryRepository.findActiveFamilyMemories(
                familyId, viewerUserId, normalizeLimit(limit));
        entries.forEach(entry -> memoryVoteService.attachVoteStats(entry, viewerUserId));
        return entries.stream()
                .sorted((left, right) -> Integer.compare(
                        MemoryVoteService.voteStatsFromMetadata(right).getVoteScore(),
                        MemoryVoteService.voteStatsFromMetadata(left).getVoteScore()))
                .toList();
    }

    public PageResult<MemoryEntry> searchFamilyMemories(
            Long familyId, Long targetUserId, String keyword, int page, int pageSize) {
        familyService.checkMembership(familyId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        int normalizedPageSize = normalizePageSize(pageSize);
        String normalizedKeyword = normalizeKeyword(keyword);
        long total = memoryRepository.countActiveFamilyMemoriesSearch(
                familyId, viewerUserId, targetUserId, normalizedKeyword);
        long resolvedPage = resolvePage(page, normalizedPageSize, total);
        long offset = (resolvedPage - 1L) * normalizedPageSize;
        List<MemoryEntry> items = total == 0
                ? List.of()
                : memoryRepository.searchActiveFamilyMemories(
                        familyId, viewerUserId, targetUserId, normalizedKeyword, normalizedPageSize, offset);
        items.forEach(entry -> memoryVoteService.attachVoteStats(entry, viewerUserId));
        return PageResult.of(items, resolvedPage, normalizedPageSize, total);
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private static int normalizePageSize(int pageSize) {
        if (pageSize <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private static long resolvePage(int page, int pageSize, long total) {
        long normalizedPage = Math.max(page, 1);
        if (total <= 0) return normalizedPage;
        long totalPages = (total + pageSize - 1L) / pageSize;
        return Math.min(normalizedPage, totalPages);
    }
}
