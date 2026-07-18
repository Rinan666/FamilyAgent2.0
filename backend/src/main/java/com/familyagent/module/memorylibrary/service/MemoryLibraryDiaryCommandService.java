package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.MemoryLibraryDiaryFacade;
import com.familyagent.module.memory.facade.MemoryIndexingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryEmbeddingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryIndexMetadataFacade;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MemoryLibraryDiaryCommandService {

    private static final Set<String> ENTRY_TYPES = Set.of(
            "DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION");

    private final MemoryLibraryDiaryFacade diaryFacade;
    private final MemoryIndexingFacade indexingFacade;
    private final MemoryLibraryEmbeddingFacade embeddingFacade;
    private final MemoryLibraryIndexMetadataFacade metadataFacade;

    public void archive(Long familyId, Long diaryId) {
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

    public void update(MemoryLibraryUpdateRequest request, Long diaryId) {
        DiaryEntry entry = diaryFacade.findById(diaryId);
        if (entry == null || !request.getFamilyId().equals(entry.getFamilyId())
                || MemoryLibraryCommandSupport.isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can edit this diary");
        String body = MemoryLibraryCommandSupport.requiredBody(request.getBody());
        String type = MemoryLibraryCommandSupport.normalize(
                request.getType(), "DAILY", ENTRY_TYPES, "Diary entry type is not supported");
        String visibility = MemoryLibraryCommandSupport.normalize(
                request.getVisibility(), entry.getVisibility(), MemoryScope.diaryNames(),
                "Diary visibility is not supported");
        String[] tags = MemoryLibraryCommandSupport.normalizedTags(request.getTags());
        entry.setRawText(body);
        entry.setStructured(MemoryLibraryCommandSupport.diaryStructured(type, request.getTitle(), body));
        entry.setTags(tags);
        entry.setVisibility(visibility);
        entry.setPrivacyLevel(visibility);
        entry.setMetadata(metadataFacade.enrichDiary(
                MemoryLibraryCommandSupport.editMetadata(entry.getMetadata()),
                entry.getRawText(),
                type,
                entry.getMood(),
                tags));
        diaryFacade.update(entry);
        indexingFacade.indexDiaryAfterCommit(entry);
    }

    public void restore(Long familyId, Long diaryId) {
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

    public void deleteArchived(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryFacade.findById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || !MemoryLibraryCommandSupport.isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can delete this diary");
        embeddingFacade.deleteDiaryIndex(diaryId);
        diaryFacade.delete(diaryId);
    }
}
