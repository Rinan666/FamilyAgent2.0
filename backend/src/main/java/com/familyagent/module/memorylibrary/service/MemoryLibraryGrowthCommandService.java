package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.MemoryLibraryGrowthFacade;
import com.familyagent.module.memory.facade.MemoryIndexingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryEmbeddingFacade;
import com.familyagent.module.memory.service.MemoryIndexMetadataBuilder;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MemoryLibraryGrowthCommandService {

    private static final Set<String> CATEGORIES = Set.of(
            "POSTURE", "DENTAL", "VISION", "SLEEP", "EXERCISE", "SCREEN_TIME",
            "EMOTION", "COMMUNICATION", "OTHER");

    private final MemoryLibraryGrowthFacade growthFacade;
    private final MemoryIndexingFacade indexingFacade;
    private final MemoryLibraryEmbeddingFacade embeddingFacade;

    public void archive(Long familyId, Long recordId) {
        GrowthGuardRecord record = findRecord(familyId, recordId, EntityStatus.ACTIVE);
        MemoryLibrarySupport.ensureCreator(
                record.getCreatedBy(),
                "Only the creator can archive this growth record");
        record.setStatus(EntityStatus.ARCHIVED.name());
        growthFacade.update(record);
    }

    public void update(MemoryLibraryUpdateRequest request, Long recordId) {
        GrowthGuardRecord record = findRecord(request.getFamilyId(), recordId, EntityStatus.ACTIVE);
        MemoryLibrarySupport.ensureCreator(
                record.getCreatedBy(),
                "Only the creator can edit this growth record");
        String category = MemoryLibraryCommandSupport.normalize(
                request.getType(), record.getCategory(), CATEGORIES,
                "Growth category is not supported");
        String visibility = MemoryLibraryCommandSupport.normalize(
                request.getVisibility(), record.getVisibility(), MemoryScope.familyNames(),
                "Growth visibility is not supported");
        Map<String, Object> metadata = MemoryLibraryCommandSupport.editMetadata(record.getMetadata());
        List<String> tags = List.of(MemoryLibraryCommandSupport.normalizedTags(request.getTags()));
        if (tags.isEmpty()) {
            metadata.remove("tags");
        } else {
            metadata.put("tags", tags);
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
        indexingFacade.indexGrowthAfterCommit(record);
    }

    public void restore(Long familyId, Long recordId) {
        GrowthGuardRecord record = findRecord(familyId, recordId, EntityStatus.ARCHIVED);
        MemoryLibrarySupport.ensureCreator(
                record.getCreatedBy(),
                "Only the creator can restore this growth record");
        record.setStatus(EntityStatus.ACTIVE.name());
        growthFacade.update(record);
    }

    public void deleteArchived(Long familyId, Long recordId) {
        GrowthGuardRecord record = findRecord(familyId, recordId, EntityStatus.ARCHIVED);
        MemoryLibrarySupport.ensureCreator(
                record.getCreatedBy(),
                "Only the creator can delete this growth record");
        embeddingFacade.deleteGrowthIndex(recordId);
        growthFacade.delete(recordId);
    }

    private GrowthGuardRecord findRecord(Long familyId, Long recordId, EntityStatus status) {
        GrowthGuardRecord record = growthFacade.findById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId())
                || !status.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return record;
    }
}
