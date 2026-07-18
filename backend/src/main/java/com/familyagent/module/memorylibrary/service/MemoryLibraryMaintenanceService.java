package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.MemoryLibraryDiaryFacade;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles memory library archive, restore, and deletion commands.
 */
@Component
@RequiredArgsConstructor
public class MemoryLibraryMaintenanceService {

    private static final Set<String> DIARY_ENTRY_TYPES = Set.of(
            "DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION");
    private static final Set<String> GROWTH_CATEGORIES = Set.of(
            "POSTURE", "DENTAL", "VISION", "SLEEP", "EXERCISE", "SCREEN_TIME", "EMOTION", "COMMUNICATION", "OTHER");

    private final MemoryLibraryFamilyFacade familyService;
    private final MemoryLibraryDiaryFacade diaryFacade;
    private final MemoryLibraryGrowthFacade growthFacade;
    private final MemoryIndexingFacade memoryEmbeddingService;
    private final MemoryLibraryEmbeddingFacade embeddingFacade;
    private final MemoryLibraryMemoryCommandService memoryCommands;

    @Transactional
    public void updateItem(MemoryLibraryUpdateRequest request) {
        if (request.getFamilyId() == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId cannot be null");
        familyService.checkMembership(request.getFamilyId());
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(request.getItemId());
        switch (parsed.prefix()) {
            case "diary" -> updateDiary(request, parsed.id());
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
            case "diary" -> archiveDiary(familyId, parsed.id());
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
            case "diary" -> restoreDiary(familyId, parsed.id());
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
            case "diary" -> deleteArchivedDiary(familyId, parsed.id());
            case "memory" -> memoryCommands.deleteArchived(familyId, parsed.id());
            case "growth" -> deleteArchivedGrowthRecord(familyId, parsed.id());
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported memory library item type");
        }
    }

    private void archiveDiary(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryFacade.findById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || MemoryLibraryCommandSupport.isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can archive this diary");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("status", EntityStatus.ARCHIVED.name());
        metadata.put("archivedBy", CurrentUserGuard.currentUserId());
        metadata.put("archivedAt", LocalDateTime.now().toString());
        metadata.put("archiveSource", "MEMORY_LIBRARY_MAINTENANCE");
        entry.setMetadata(metadata);
        diaryFacade.update(entry);
    }

    private void updateDiary(MemoryLibraryUpdateRequest request, Long diaryId) {
        DiaryEntry entry = diaryFacade.findById(diaryId);
        if (entry == null || !request.getFamilyId().equals(entry.getFamilyId())
                || MemoryLibraryCommandSupport.isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can edit this diary");
        String body = MemoryLibraryCommandSupport.requiredBody(request.getBody());
        String type = MemoryLibraryCommandSupport.normalize(
                request.getType(), "DAILY", DIARY_ENTRY_TYPES, "Diary entry type is not supported");
        String visibility = MemoryLibraryCommandSupport.normalize(
                request.getVisibility(), entry.getVisibility(), MemoryScope.diaryNames(),
                "Diary visibility is not supported");
        String[] tags = MemoryLibraryCommandSupport.normalizedTags(request.getTags());
        entry.setRawText(body);
        entry.setStructured(MemoryLibraryCommandSupport.diaryStructured(type, request.getTitle(), body));
        entry.setTags(tags);
        entry.setVisibility(visibility);
        entry.setPrivacyLevel(visibility);
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichDiary(
                MemoryLibraryCommandSupport.editMetadata(entry.getMetadata()),
                entry.getRawText(),
                type,
                entry.getMood(),
                tags));
        diaryFacade.update(entry);
        memoryEmbeddingService.indexDiaryAfterCommit(entry);
    }

    private void restoreDiary(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryFacade.findById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || !MemoryLibraryCommandSupport.isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can restore this diary");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("status", EntityStatus.ACTIVE.name());
        metadata.put("restoredBy", CurrentUserGuard.currentUserId());
        metadata.put("restoredAt", LocalDateTime.now().toString());
        metadata.put("restoreSource", "MEMORY_LIBRARY_ARCHIVE_BOX");
        entry.setMetadata(metadata);
        diaryFacade.update(entry);
    }

    private void deleteArchivedDiary(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryFacade.findById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || !MemoryLibraryCommandSupport.isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can delete this diary");
        embeddingFacade.deleteDiaryIndex(diaryId);
        diaryFacade.delete(diaryId);
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
