package com.familyagent.module.mirror.service;

import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import com.familyagent.module.memorylibrary.service.MemoryLibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MirrorContextLibraryService {

    private static final int LIBRARY_CONTEXT_LIMIT = 8;

    private final MemoryLibraryService memoryLibraryService;

    public List<MemoryLibraryItem> recallLibraryItems(Long familyId, Long targetUserId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<MemoryLibraryItem> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        appendLibraryItems(items, ids, searchLibrary(familyId, targetUserId, query, "ALL", 5));
        appendLibraryItems(items, ids, searchLibrary(familyId, null, query, "FAMILY_EXPERIENCE", 3));

        return items.size() <= LIBRARY_CONTEXT_LIMIT
                ? items
                : items.subList(0, LIBRARY_CONTEXT_LIMIT);
    }

    private List<MemoryLibraryItem> searchLibrary(
            Long familyId,
            Long memberUserId,
            String query,
            String type,
            int pageSize) {
        MemoryLibrarySearchRequest request = new MemoryLibrarySearchRequest();
        request.setFamilyId(familyId);
        request.setPage(1);
        request.setPageSize(pageSize);
        request.setKeyword(query);
        request.setType(type);
        request.setMemberUserId(memberUserId);
        var page = memoryLibraryService.search(request);
        if (page == null || page.getItems() == null) {
            return List.of();
        }
        return page.getItems();
    }

    private static void appendLibraryItems(
            List<MemoryLibraryItem> target,
            Set<String> ids,
            List<MemoryLibraryItem> candidates) {
        for (MemoryLibraryItem item : candidates) {
            if (item.getId() != null && ids.add(item.getId())) {
                target.add(item);
            }
            if (target.size() >= LIBRARY_CONTEXT_LIMIT) {
                return;
            }
        }
    }
}
