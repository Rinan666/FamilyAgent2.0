package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.facade.MemoryLibraryFamilyFacade;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.MemoryLibraryGrowthFacade;
import com.familyagent.module.memory.facade.MemoryIndexingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryEmbeddingFacade;
import com.familyagent.module.memory.service.MemoryIndexMetadataBuilder;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles memory library archive, restore, and deletion commands.
 */
@Component
@RequiredArgsConstructor
public class MemoryLibraryMaintenanceService {

    private static final Set<String> GROWTH_CATEGORIES = Set.of(
            "POSTURE", "DENTAL", "VISION", "SLEEP", "EXERCISE", "SCREEN_TIME", "EMOTION", "COMMUNICATION", "OTHER");

    private final MemoryLibraryFamilyFacade familyService;
    private final MemoryLibraryGrowthFacade growthFacade;
    private final MemoryIndexingFacade memoryEmbeddingService;
    private final MemoryLibraryEmbeddingFacade embeddingFacade;
    private final MemoryLibraryMemoryCommandService memoryCommands;
    private final MemoryLibraryDiaryCommandService diaryCommands;

    @Transactional
    public void updateItem(MemoryLibraryUpdateRequest request) {
        if (request.getFamilyId() == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId cannot be null");
        familyService.checkMembership(request.getFamilyId());
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(request.getItemId());
        switch (parsed.prefix()) {
            case "diary" -> diaryCommands.update(request, parsed.id());
            case "memory" -> memoryCommands.update(request, parsed.id());
            case "growth" -> updateGrowthRecord(request, parsed.id());
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
            case "growth" -> archiveGrowthRecord(familyId, parsed.id());
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
            case "growth" -> restoreGrowthRecord(familyId, parsed.id());
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
            case "growth" -> deleteArchivedGrowthRecord(familyId, parsed.id());
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported memory library item type");
        }
    }

    private void archiveGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthFacade.findById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !EntityStatus.ACTIVE.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(record.getCreatedBy(), "Only the creator can archive this growth record");
        record.setStatus(EntityStatus.ARCHIVED.name());
        growthFacade.update(record);
    }

    private void updateGrowthRecord(MemoryLibraryUpdateRequest request, Long recordId) {
        GrowthGuardRecord record = growthFacade.findById(recordId);
        if (record == null || !request.getFamilyId().equals(record.getFamilyId()) || !EntityStatus.ACTIVE.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(record.getCreatedBy(), "Only the creator can edit this growth record");
        String category = MemoryLibraryCommandSupport.normalize(
                request.getType(), record.getCategory(), GROWTH_CATEGORIES,
                "Growth category is not supported");
        String visibility = MemoryLibraryCommandSupport.normalize(
                request.getVisibility(), record.getVisibility(), MemoryScope.familyNames(),
                "Growth visibility is not supported");
        Map<String, Object> metadata = MemoryLibraryCommandSupport.editMetadata(record.getMetadata());
        List<String> tags = List.of(MemoryLibraryCommandSupport.normalizedTags(request.getTags()));
        if (!tags.isEmpty()) {
            metadata.put("tags", tags);
        } else {
            metadata.remove("tags");
        }
        record.setContent(MemoryLibraryCommandSupport.requiredBody(request.getBody()));
        record.setCategory(category);
        record.setVisibility(visibility);
        record.setMetadata(MemoryIndexMetadataBuilder.enrichGrowth(
                metadata,
                record.getContent(),
                record.getCategory(),
                record.getSeverity() == null ? 3 : record.getSeverity(),
                record.getObservedAt()));
        growthFacade.update(record);
        memoryEmbeddingService.indexGrowthAfterCommit(record);
    }

    private void restoreGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthFacade.findById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !EntityStatus.ARCHIVED.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(record.getCreatedBy(), "Only the creator can restore this growth record");
        record.setStatus(EntityStatus.ACTIVE.name());
        growthFacade.update(record);
    }

    private void deleteArchivedGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthFacade.findById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !EntityStatus.ARCHIVED.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(record.getCreatedBy(), "Only the creator can delete this growth record");
        embeddingFacade.deleteGrowthIndex(recordId);
        growthFacade.delete(recordId);
    }

}
