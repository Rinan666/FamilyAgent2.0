package com.familyagent.module.growth.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.facade.UnifiedMemorySyncFacade;
import com.familyagent.module.memory.facade.UnifiedMemoryCreateResult;
import com.familyagent.module.memory.facade.UnifiedMemorySyncMetadata;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GrowthMemorySyncSupport {

    private final UnifiedMemorySyncFacade syncFacade;

    public void create(GrowthGuardRecord record) {
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(java.time.LocalDateTime.now());
        }
        UnifiedMemoryCreateResult result = syncFacade.create(request(record, null));
        record.setId(result.originId());
        record.setMemoryEntryId(result.memoryEntryId());
        record.setCreatedAt(result.createdAt());
        record.setUpdatedAt(result.updatedAt());
    }

    public void sync(GrowthGuardRecord record) {
        syncFacade.sync(request(record, record.getId()));
    }

    private static UnifiedMemorySyncRequest request(GrowthGuardRecord record, Long originId) {
        Map<String, Object> metadata = metadata(record.getMetadata());
        return new UnifiedMemorySyncRequest(
                record.getCreatedBy(),
                record.getFamilyId(),
                record.getTargetUserId(),
                MemoryContentType.OBSERVATION,
                MemoryScope.valueOf(record.getVisibility()),
                text(metadata.get("title")),
                record.getContent(),
                stringList(metadata.get("tags")),
                record.getObservedAt() == null ? record.getCreatedAt() : record.getObservedAt().atStartOfDay(),
                MemoryOriginType.GROWTH,
                originId,
                UnifiedMemorySyncMetadata.growth(
                        record.getCategory(),
                        record.getSeverity(),
                        record.getFollowUpAt(),
                        metadata),
                EntityStatus.valueOf(record.getStatus()));
    }

    public void delete(Long recordId) {
        syncFacade.delete(MemoryOriginType.GROWTH, recordId);
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : iterable) {
            String text = text(item);
            if (text != null) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(Object value) {
        return value instanceof Map<?, ?> map
                ? new HashMap<>((Map<String, Object>) map)
                : new HashMap<>();
    }
}
