package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryLibraryMetadataSource;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.constant.MemoryType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.facade.MemoryIndexingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryEmbeddingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryIndexMetadataFacade;
import com.familyagent.module.memory.facade.MemoryLibraryMemoryFacade;
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
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("archivedBy", CurrentUserGuard.currentUserId());
        metadata.put("archivedAt", LocalDateTime.now().toString());
        metadata.put("archiveSource", MemoryLibraryMetadataSource.MEMORY_LIBRARY_MAINTENANCE.name());
        entry.setMetadata(metadata);
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
        String type = MemoryLibraryCommandSupport.normalize(
                request.getType(), entry.getType(), MemoryType.names(), "Memory type is not supported");
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
        entry.setSummary(MemoryLibraryCommandSupport.summaryFrom(request.getTitle(), body));
        entry.setType(type);
        entry.setScope(visibility);
        entry.setMetadata(metadataFacade.enrichMemory(
                metadata,
                entry.getContent(),
                entry.getSummary(),
                entry.getType(),
                entry.getImportance() == null ? 3 : entry.getImportance()));
        memoryFacade.update(entry);
        indexingFacade.indexMemoryAfterCommit(entry);
    }

    public void restore(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryFacade.findById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || !EntityStatus.ARCHIVED.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreator(entry.getUserId(), "Only the creator can restore this memory");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("restoredBy", CurrentUserGuard.currentUserId());
        metadata.put("restoredAt", LocalDateTime.now().toString());
        metadata.put("restoreSource", MemoryLibraryMetadataSource.MEMORY_LIBRARY_ARCHIVE_BOX.name());
        entry.setMetadata(metadata);
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
