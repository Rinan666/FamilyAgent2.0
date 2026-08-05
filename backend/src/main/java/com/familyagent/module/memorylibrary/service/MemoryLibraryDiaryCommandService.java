package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryLibraryMetadataSource;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.MemoryLibraryDiaryFacade;
import com.familyagent.module.memory.facade.MemoryLibraryIndexMetadataFacade;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryAuditMetadata;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MemoryLibraryDiaryCommandService {

    private static final Set<String> ENTRY_TYPES = Set.of(
            "DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION");

    private final MemoryLibraryDiaryFacade diaryFacade;
    private final MemoryLibraryIndexMetadataFacade metadataFacade;

    public void archive(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryFacade.findById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || MemoryLibraryCommandSupport.isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can archive this diary");
        entry.setMetadata(new MemoryLibraryAuditMetadata(
                CurrentUserGuard.currentUserId(),
                LocalDateTime.now(),
                MemoryLibraryMetadataSource.MEMORY_LIBRARY_MAINTENANCE)
                .mergeArchive(entry.getMetadata(), EntityStatus.ARCHIVED));
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
        String type = normalizeEntryType(request.getType());
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
    }

    private static String normalizeEntryType(String requestedType) {
        String requested = MemoryLibrarySupport.blankToNull(requestedType);
        if (requested == null) {
            return "DAILY";
        }
        String normalized = requested.toUpperCase(java.util.Locale.ROOT);
        if (ENTRY_TYPES.contains(normalized)) {
            return normalized;
        }
        MemoryContentType contentType = MemoryContentType.fromValue(normalized);
        if (contentType == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Diary entry type is not supported");
        }
        return switch (contentType) {
            case KNOWLEDGE -> "LESSON";
            case INSIGHT -> "SELF_REFLECTION";
            default -> "DAILY";
        };
    }

    public void restore(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryFacade.findById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || !MemoryLibraryCommandSupport.isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can restore this diary");
        entry.setMetadata(new MemoryLibraryAuditMetadata(
                CurrentUserGuard.currentUserId(),
                LocalDateTime.now(),
                MemoryLibraryMetadataSource.MEMORY_LIBRARY_ARCHIVE_BOX)
                .mergeRestore(entry.getMetadata(), EntityStatus.ACTIVE));
        diaryFacade.update(entry);
    }

    public void deleteArchived(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryFacade.findById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || !MemoryLibraryCommandSupport.isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can delete this diary");
        diaryFacade.delete(diaryId);
    }
}
