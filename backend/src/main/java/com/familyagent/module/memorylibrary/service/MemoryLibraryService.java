package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.response.PageResult;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryMaintenanceSuggestion;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryLibraryService {

    private final MemoryLibraryQueryService queryService;
    private final MemoryLibraryMaintenanceService maintenanceService;
    private final MemoryLibraryMergeService mergeService;
    private final MemoryLibraryClassicalizeService classicalizeService;

    public PageResult<MemoryLibraryItem> search(MemoryLibrarySearchRequest request) {
        return queryService.search(request);
    }

    public PageResult<MemoryLibraryItem> archived(MemoryLibrarySearchRequest request) {
        return queryService.archived(request);
    }

    public List<MemoryLibraryMaintenanceSuggestion> maintenanceSuggestions(MemoryLibrarySearchRequest request) {
        return maintenanceService.maintenanceSuggestions(request);
    }

    public void classicalizeLibraryItem(
            Long familyId, String itemId, String classicalText, String plainSummary, String styleNote) {
        classicalizeService.classicalize(familyId, itemId, classicalText, plainSummary, styleNote);
    }

    public void mergeLibraryItems(Long familyId, String primaryItemId, String secondaryItemId) {
        mergeService.mergeItems(familyId, primaryItemId, secondaryItemId);
    }

    public void archiveLibraryItem(Long familyId, String itemId) {
        maintenanceService.archiveItem(familyId, itemId);
    }

    public void restoreLibraryItem(Long familyId, String itemId) {
        maintenanceService.restoreItem(familyId, itemId);
    }

    public void deleteArchivedLibraryItem(Long familyId, String itemId) {
        maintenanceService.deleteArchivedItem(familyId, itemId);
    }
}
