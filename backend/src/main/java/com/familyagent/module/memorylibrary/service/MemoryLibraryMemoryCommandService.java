package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryLibraryMetadataSource;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.facade.MemoryIndexingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryEmbeddingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryIndexMetadataFacade;
import com.familyagent.module.memory.facade.MemoryLibraryMemoryFacade;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryAuditMetadata;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MemoryLibraryMemoryCommandService {

    private final MemoryLibraryMemoryFacade memoryFacade;
    private final MemoryIndexingFacade indexingFacade;
    private final MemoryLibraryEmbeddingFacade embeddingFacade;
    private final MemoryLibraryIndexMetadataFacade metadataFacade;

    public void archive(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryFacade.findById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || !EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can archive this memory");
        entry.setMetadata(new MemoryLibraryAuditMetadata(
                CurrentUserGuard.currentUserId(),
                LocalDateTime.now(),
                MemoryLibraryMetadataSource.MEMORY_LIBRARY_MAINTENANCE)
                .mergeArchive(entry.getMetadata(), null));
        entry.setStatus(EntityStatus.ARCHIVED.name());
        memoryFacade.update(entry);
    }

    public void update(MemoryLibraryUpdateRequest request, Long memoryId) {
        MemoryEntry entry = memoryFacade.findById(memoryId);
        if (entry == null || !request.getFamilyId().equals(entry.getFamilyId())
                || !EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can edit this memory");
        String body = MemoryLibraryCommandSupport.requiredBody(request.getBody());
        String type = normalizeType(request.getType(), entry.getType());
        String visibility = MemoryLibraryCommandSupport.normalize(
                request.getVisibility(), entry.getScope(), MemoryScope.familyNames(),
                "Memory visibility is not supported");
        Map<String, Object> metadata = MemoryLibraryCommandSupport.editMetadata(entry.getMetadata());
        List<String> tags = List.of(MemoryLibraryCommandSupport.normalizedTags(request.getTags()));
        if (tags.isEmpty()) {
            metadata.remove("tags");
        } else {
            metadata.put("tags", tags);
        }
        entry.setContent(body);
        entry.setTitle(MemoryLibrarySupport.blankToNull(request.getTitle()));
        entry.setSummary(MemoryLibraryCommandSupport.summaryFrom(request.getTitle(), body));
        entry.setType(type);
        entry.setScope(visibility);
        entry.setTags(MemoryLibraryCommandSupport.normalizedTags(request.getTags()));
        entry.setMetadata(metadataFacade.enrichMemory(
                metadata,
                entry.getContent(),
                entry.getSummary(),
                entry.getType(),
                entry.getImportance() == null ? 3 : entry.getImportance()));
        memoryFacade.update(entry);
        indexingFacade.indexMemoryAfterCommit(entry);
    }

    private static String normalizeType(String requestedType, String fallbackType) {
        String requested = MemoryLibrarySupport.blankToNull(requestedType);
        MemoryContentType resolved = MemoryContentType.fromValue(
                requested == null ? fallbackType : requested);
        if (resolved == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Memory type is not supported");
        }
        return resolved.name();
    }

    public void restore(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryFacade.findById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || !EntityStatus.ARCHIVED.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can restore this memory");
        entry.setMetadata(new MemoryLibraryAuditMetadata(
                CurrentUserGuard.currentUserId(),
                LocalDateTime.now(),
                MemoryLibraryMetadataSource.MEMORY_LIBRARY_ARCHIVE_BOX)
                .mergeRestore(entry.getMetadata(), null));
        entry.setStatus(EntityStatus.ACTIVE.name());
        memoryFacade.update(entry);
    }

    public void deleteArchived(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryFacade.findById(memoryId);
        boolean activeLegacyAiSummary = entry != null
                && familyId.equals(entry.getFamilyId())
                && EntityStatus.ACTIVE.name().equals(entry.getStatus())
                && MemoryLibrarySupport.isLegacyAiSummary(entry.getMetadata());
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || (!EntityStatus.ARCHIVED.name().equals(entry.getStatus()) && !activeLegacyAiSummary)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can delete this memory");
        embeddingFacade.deleteMemoryIndex(memoryId);
        memoryFacade.delete(memoryId);
    }
}
