package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.service.GrowthGuardService;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Handles memory library archive, restore, and deletion commands.
 */
@Component
@RequiredArgsConstructor
public class MemoryLibraryMaintenanceService {

    private final FamilyService familyService;
    private final DiaryEntryRepository diaryEntryRepository;
    private final MemoryEntryRepository memoryEntryRepository;
    private final GrowthGuardRecordRepository growthRecordRepository;
    private final GrowthGuardService growthGuardService;
    private final JdbcTemplate jdbcTemplate;

    public void archiveItem(Long familyId, String itemId) {
        if (familyId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId cannot be null");
        familyService.checkMembership(familyId);
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(itemId);
        switch (parsed.prefix()) {
            case "diary" -> archiveDiary(familyId, parsed.id());
            case "memory" -> archiveMemory(familyId, parsed.id());
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
            case "memory" -> restoreMemory(familyId, parsed.id());
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
            case "memory" -> deleteArchivedMemory(familyId, parsed.id());
            case "growth" -> deleteArchivedGrowthRecord(familyId, parsed.id());
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported memory library item type");
        }
    }

    private void archiveDiary(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryEntryRepository.selectById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(
                familyService, familyId, entry.getUserId(), "Only the creator or family owner can archive this diary");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("status", EntityStatus.ARCHIVED.name());
        metadata.put("archivedBy", CurrentUserGuard.currentUserId());
        metadata.put("archivedAt", LocalDateTime.now().toString());
        metadata.put("archiveSource", "MEMORY_LIBRARY_MAINTENANCE");
        entry.setMetadata(metadata);
        diaryEntryRepository.updateById(entry);
    }

    private void restoreDiary(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryEntryRepository.selectById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(
                familyService, familyId, entry.getUserId(), "Only the creator or family owner can restore this diary");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("status", EntityStatus.ACTIVE.name());
        metadata.put("restoredBy", CurrentUserGuard.currentUserId());
        metadata.put("restoredAt", LocalDateTime.now().toString());
        metadata.put("restoreSource", "MEMORY_LIBRARY_ARCHIVE_BOX");
        entry.setMetadata(metadata);
        diaryEntryRepository.updateById(entry);
    }

    private void deleteArchivedDiary(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryEntryRepository.selectById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(
                familyService, familyId, entry.getUserId(), "Only the creator or family owner can delete this diary");
        deleteEmbeddings("DIARY", diaryId);
        diaryEntryRepository.deleteById(diaryId);
    }

    private void archiveMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.selectById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(
                familyService, familyId, entry.getUserId(), "Only the creator or family owner can archive this memory");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("archivedBy", CurrentUserGuard.currentUserId());
        metadata.put("archivedAt", LocalDateTime.now().toString());
        metadata.put("archiveSource", "MEMORY_LIBRARY_MAINTENANCE");
        entry.setMetadata(metadata);
        entry.setStatus(EntityStatus.ARCHIVED.name());
        memoryEntryRepository.updateById(entry);
    }

    private void restoreMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.selectById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !EntityStatus.ARCHIVED.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(
                familyService, familyId, entry.getUserId(), "Only the creator or family owner can restore this memory");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("restoredBy", CurrentUserGuard.currentUserId());
        metadata.put("restoredAt", LocalDateTime.now().toString());
        metadata.put("restoreSource", "MEMORY_LIBRARY_ARCHIVE_BOX");
        entry.setMetadata(metadata);
        entry.setStatus(EntityStatus.ACTIVE.name());
        memoryEntryRepository.updateById(entry);
    }

    private void deleteArchivedMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.selectById(memoryId);
        boolean activeLegacyAiSummary = entry != null
                && familyId.equals(entry.getFamilyId())
                && EntityStatus.ACTIVE.name().equals(entry.getStatus())
                && MemoryLibrarySupport.isLegacyAiSummary(entry.getMetadata());
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || (!EntityStatus.ARCHIVED.name().equals(entry.getStatus()) && !activeLegacyAiSummary)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(
                familyService, familyId, entry.getUserId(), "Only the creator or family owner can delete this memory");
        deleteEmbeddings("MEMORY", memoryId);
        memoryEntryRepository.deleteById(memoryId);
    }

    private void archiveGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !EntityStatus.ACTIVE.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        growthGuardService.archiveRecord(recordId);
    }

    private void restoreGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !EntityStatus.ARCHIVED.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(
                familyService, familyId, record.getCreatedBy(), "Only the creator or family owner can restore this growth record");
        record.setStatus(EntityStatus.ACTIVE.name());
        growthRecordRepository.updateById(record);
    }

    private void deleteArchivedGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !EntityStatus.ARCHIVED.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(
                familyService, familyId, record.getCreatedBy(), "Only the creator or family owner can delete this growth record");
        deleteEmbeddings("GROWTH_OBSERVATION", recordId);
        growthRecordRepository.deleteById(recordId);
    }

    private void deleteEmbeddings(String sourceType, Long sourceId) {
        jdbcTemplate.update("DELETE FROM memory_embeddings WHERE source_type = ? AND source_id = ?", sourceType, sourceId);
    }

    private static boolean isArchivedMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return EntityStatus.ARCHIVED.name().equalsIgnoreCase(String.valueOf(map.get("status")));
        }
        return false;
    }
}
