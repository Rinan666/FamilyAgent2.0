package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.constant.MemoryType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.facade.MemoryLibraryFamilyFacade;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.facade.MemoryIndexingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryMemoryFacade;
import com.familyagent.module.memory.service.MemoryIndexMetadataBuilder;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final DiaryEntryRepository diaryEntryRepository;
    private final MemoryLibraryMemoryFacade memoryEntryRepository;
    private final GrowthGuardRecordRepository growthRecordRepository;
    private final MemoryIndexingFacade memoryEmbeddingService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void updateItem(MemoryLibraryUpdateRequest request) {
        if (request.getFamilyId() == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId cannot be null");
        familyService.checkMembership(request.getFamilyId());
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(request.getItemId());
        switch (parsed.prefix()) {
            case "diary" -> updateDiary(request, parsed.id());
            case "memory" -> updateMemory(request, parsed.id());
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
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can archive this diary");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("status", EntityStatus.ARCHIVED.name());
        metadata.put("archivedBy", CurrentUserGuard.currentUserId());
        metadata.put("archivedAt", LocalDateTime.now().toString());
        metadata.put("archiveSource", "MEMORY_LIBRARY_MAINTENANCE");
        entry.setMetadata(metadata);
        diaryEntryRepository.updateById(entry);
    }

    private void updateDiary(MemoryLibraryUpdateRequest request, Long diaryId) {
        DiaryEntry entry = diaryEntryRepository.selectById(diaryId);
        if (entry == null || !request.getFamilyId().equals(entry.getFamilyId()) || isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can edit this diary");
        String body = requiredBody(request.getBody());
        String type = normalize(request.getType(), "DAILY", DIARY_ENTRY_TYPES, "Diary entry type is not supported");
        String visibility = normalize(request.getVisibility(), entry.getVisibility(), MemoryScope.diaryNames(), "Diary visibility is not supported");
        String[] tags = normalizedTags(request.getTags());
        entry.setRawText(body);
        entry.setStructured(buildDiaryStructured(type, request.getTitle(), body));
        entry.setTags(tags);
        entry.setVisibility(visibility);
        entry.setPrivacyLevel(visibility);
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichDiary(
                editMetadata(entry.getMetadata()),
                entry.getRawText(),
                type,
                entry.getMood(),
                tags));
        diaryEntryRepository.updateById(entry);
        memoryEmbeddingService.indexDiaryAfterCommit(entry);
    }

    private void restoreDiary(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryEntryRepository.selectById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can restore this diary");
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
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can delete this diary");
        deleteEmbeddings("DIARY", diaryId);
        diaryEntryRepository.deleteById(diaryId);
    }

    private void archiveMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.findById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can archive this memory");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("archivedBy", CurrentUserGuard.currentUserId());
        metadata.put("archivedAt", LocalDateTime.now().toString());
        metadata.put("archiveSource", "MEMORY_LIBRARY_MAINTENANCE");
        entry.setMetadata(metadata);
        entry.setStatus(EntityStatus.ARCHIVED.name());
        memoryEntryRepository.update(entry);
    }

    private void updateMemory(MemoryLibraryUpdateRequest request, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.findById(memoryId);
        if (entry == null || !request.getFamilyId().equals(entry.getFamilyId()) || !EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can edit this memory");
        String body = requiredBody(request.getBody());
        String type = normalize(request.getType(), entry.getType(), MemoryType.names(), "Memory type is not supported");
        String visibility = normalize(request.getVisibility(), entry.getScope(), MemoryScope.familyNames(), "Memory visibility is not supported");
        Map<String, Object> metadata = editMetadata(entry.getMetadata());
        List<String> tags = List.of(normalizedTags(request.getTags()));
        if (!tags.isEmpty()) {
            metadata.put("tags", tags);
        } else {
            metadata.remove("tags");
        }
        entry.setContent(body);
        entry.setSummary(summaryFrom(request.getTitle(), body));
        entry.setType(type);
        entry.setScope(visibility);
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichFamilyMemory(
                metadata,
                entry.getContent(),
                entry.getSummary(),
                entry.getType(),
                entry.getImportance() == null ? 3 : entry.getImportance()));
        memoryEntryRepository.update(entry);
        memoryEmbeddingService.indexMemoryAfterCommit(entry);
    }

    private void restoreMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.findById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !EntityStatus.ARCHIVED.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can restore this memory");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("restoredBy", CurrentUserGuard.currentUserId());
        metadata.put("restoredAt", LocalDateTime.now().toString());
        metadata.put("restoreSource", "MEMORY_LIBRARY_ARCHIVE_BOX");
        entry.setMetadata(metadata);
        entry.setStatus(EntityStatus.ACTIVE.name());
        memoryEntryRepository.update(entry);
    }

    private void deleteArchivedMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.findById(memoryId);
        boolean activeLegacyAiSummary = entry != null
                && familyId.equals(entry.getFamilyId())
                && EntityStatus.ACTIVE.name().equals(entry.getStatus())
                && MemoryLibrarySupport.isLegacyAiSummary(entry.getMetadata());
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || (!EntityStatus.ARCHIVED.name().equals(entry.getStatus()) && !activeLegacyAiSummary)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can delete this memory");
        deleteEmbeddings("MEMORY", memoryId);
        memoryEntryRepository.delete(memoryId);
    }

    private void archiveGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !EntityStatus.ACTIVE.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(record.getCreatedBy(), "Only the creator can archive this growth record");
        record.setStatus(EntityStatus.ARCHIVED.name());
        growthRecordRepository.updateById(record);
    }

    private void updateGrowthRecord(MemoryLibraryUpdateRequest request, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !request.getFamilyId().equals(record.getFamilyId()) || !EntityStatus.ACTIVE.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(record.getCreatedBy(), "Only the creator can edit this growth record");
        String category = normalize(request.getType(), record.getCategory(), GROWTH_CATEGORIES, "Growth category is not supported");
        String visibility = normalize(request.getVisibility(), record.getVisibility(), MemoryScope.familyNames(), "Growth visibility is not supported");
        Map<String, Object> metadata = editMetadata(record.getMetadata());
        List<String> tags = List.of(normalizedTags(request.getTags()));
        if (!tags.isEmpty()) {
            metadata.put("tags", tags);
        } else {
            metadata.remove("tags");
        }
        record.setContent(requiredBody(request.getBody()));
        record.setCategory(category);
        record.setVisibility(visibility);
        record.setMetadata(MemoryIndexMetadataBuilder.enrichGrowth(
                metadata,
                record.getContent(),
                record.getCategory(),
                record.getSeverity() == null ? 3 : record.getSeverity(),
                record.getObservedAt()));
        growthRecordRepository.updateById(record);
        memoryEmbeddingService.indexGrowthAfterCommit(record);
    }

    private void restoreGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !EntityStatus.ARCHIVED.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(record.getCreatedBy(), "Only the creator can restore this growth record");
        record.setStatus(EntityStatus.ACTIVE.name());
        growthRecordRepository.updateById(record);
    }

    private void deleteArchivedGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !EntityStatus.ARCHIVED.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(record.getCreatedBy(), "Only the creator can delete this growth record");
        deleteEmbeddings("GROWTH_OBSERVATION", recordId);
        growthRecordRepository.deleteById(recordId);
    }

    private void deleteEmbeddings(String sourceType, Long sourceId) {
        jdbcTemplate.update("DELETE FROM memory_embeddings WHERE source_type = ? AND source_id = ?", sourceType, sourceId);
    }

    private static String requiredBody(String body) {
        String text = MemoryLibrarySupport.blankToNull(body);
        if (text == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Memory content cannot be blank");
        }
        return text;
    }

    private static String normalize(String value, String fallback, Set<String> allowed, String errorMessage) {
        String normalized = MemoryLibrarySupport.blankToNull(value);
        normalized = normalized == null ? fallback : normalized.toUpperCase(Locale.ROOT);
        if (normalized == null || !allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, errorMessage);
        }
        return normalized;
    }

    private static String[] normalizedTags(List<String> tags) {
        if (tags == null) return new String[0];
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .limit(10)
                .toArray(String[]::new);
    }

    private static Map<String, Object> buildDiaryStructured(String entryType, String title, String content) {
        Map<String, Object> structured = new HashMap<>();
        structured.put("entryType", entryType);
        structured.put("title", MemoryLibrarySupport.blankToNull(title));
        structured.put("summary", summaryFrom(title, content));
        return structured;
    }

    private static Map<String, Object> editMetadata(Object metadata) {
        Map<String, Object> next = MemoryLibrarySupport.mutableMap(metadata);
        next.put("lastEditedBy", CurrentUserGuard.currentUserId());
        next.put("lastEditedAt", LocalDateTime.now().toString());
        next.put("editSource", "MEMORY_LIBRARY_MAINTENANCE");
        return next;
    }

    private static String summaryFrom(String title, String body) {
        String text = MemoryLibrarySupport.blankToNull(title);
        if (text != null) {
            return text;
        }
        return Arrays.stream(requiredBody(body).split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .map(line -> MemoryLibrarySupport.truncateText(line, 120))
                .orElse("");
    }

    private static boolean isArchivedMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return EntityStatus.ARCHIVED.name().equalsIgnoreCase(String.valueOf(map.get("status")));
        }
        return false;
    }
}
