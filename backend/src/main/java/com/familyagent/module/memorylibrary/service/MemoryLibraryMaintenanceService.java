package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.facade.MemoryLibraryFamilyFacade;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles memory library archive, restore, and deletion commands.
 */
@Component
@RequiredArgsConstructor
public class MemoryLibraryMaintenanceService {

    private final MemoryLibraryFamilyFacade familyService;
    private final MemoryLibraryMemoryCommandService memoryCommands;
    private final MemoryLibraryDiaryCommandService diaryCommands;
    private final MemoryLibraryGrowthCommandService growthCommands;

    @Transactional
    public void updateItem(MemoryLibraryUpdateRequest request) {
        if (request.getFamilyId() == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId cannot be null");
        familyService.checkMembership(request.getFamilyId());
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(request.getItemId());
        switch (parsed.prefix()) {
            case "diary" -> diaryCommands.update(request, parsed.id());
            case "memory" -> memoryCommands.update(request, parsed.id());
            case "growth" -> growthCommands.update(request, parsed.id());
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported memory library item type");
        }
    }

    public void archiveItem(Long familyId, String itemId) {
        if (familyId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId cannot be null");
        familyService.checkMembership(familyId);
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(itemId);
        switch (parsed.prefix()) {
            case "diary" -> diaryCommands.archive(familyId, parsed.id());
            case "memory" -> memoryCommands.archive(familyId, parsed.id());
            case "growth" -> growthCommands.archive(familyId, parsed.id());
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported memory library item type");
        }
    }

    public void restoreItem(Long familyId, String itemId) {
        if (familyId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId cannot be null");
        familyService.checkMembership(familyId);
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(itemId);
        switch (parsed.prefix()) {
            case "diary" -> diaryCommands.restore(familyId, parsed.id());
            case "memory" -> memoryCommands.restore(familyId, parsed.id());
            case "growth" -> growthCommands.restore(familyId, parsed.id());
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported memory library item type");
        }
    }

    public void deleteArchivedItem(Long familyId, String itemId) {
        if (familyId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId cannot be null");
        familyService.checkMembership(familyId);
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(itemId);
        switch (parsed.prefix()) {
            case "diary" -> diaryCommands.deleteArchived(familyId, parsed.id());
            case "memory" -> memoryCommands.deleteArchived(familyId, parsed.id());
            case "growth" -> growthCommands.deleteArchived(familyId, parsed.id());
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported memory library item type");
        }
    }

}
