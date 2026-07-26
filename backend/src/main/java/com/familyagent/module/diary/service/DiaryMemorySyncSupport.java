package com.familyagent.module.diary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.module.diary.dto.DiaryEntryMetadata;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.memory.facade.UnifiedMemorySyncFacade;
import com.familyagent.module.memory.facade.UnifiedMemoryCreateResult;
import com.familyagent.module.memory.facade.UnifiedMemorySyncMetadata;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DiaryMemorySyncSupport {

    private final UnifiedMemorySyncFacade syncFacade;

    public void create(DiaryEntry entry) {
        if (entry.getCreatedAt() == null) {
            entry.setCreatedAt(LocalDateTime.now());
        }
        UnifiedMemoryCreateResult result = syncFacade.create(request(entry, null));
        entry.setId(result.originId());
        entry.setCreatedAt(result.createdAt());
        entry.setUpdatedAt(result.updatedAt());
    }

    public void sync(DiaryEntry entry) {
        syncFacade.sync(request(entry, entry.getId()));
    }

    private static UnifiedMemorySyncRequest request(DiaryEntry entry, Long originId) {
        Map<String, Object> metadata = metadata(entry.getMetadata());
        DiaryEntryMetadata typedMetadata = DiaryEntryMetadata.fromMap(metadata);
        return new UnifiedMemorySyncRequest(
                entry.getUserId(),
                entry.getFamilyId(),
                typedMetadata.getRelatedUserId(),
                MemoryContentType.fromDiaryEntryType(structuredText(entry.getStructured(), "entryType")),
                visibility(entry.getVisibility()),
                structuredText(entry.getStructured(), "title"),
                entry.getRawText(),
                entry.getTags() == null ? List.of() : Arrays.asList(entry.getTags()),
                entry.getCreatedAt(),
                MemoryOriginType.DIARY,
                originId,
                UnifiedMemorySyncMetadata.diary(
                        structuredText(entry.getStructured(), "entryType"),
                        entry.getMood(),
                        entry.getSource(),
                        entry.getVoiceUrl(),
                        metadata),
                archived(metadata) ? EntityStatus.ARCHIVED : EntityStatus.ACTIVE);
    }

    public void delete(Long diaryId) {
        syncFacade.delete(MemoryOriginType.DIARY, diaryId);
    }

    private static MemoryScope visibility(String value) {
        if ("FAMILY".equalsIgnoreCase(value)
                || MemoryScope.LEGACY_VISIBLE.name().equalsIgnoreCase(value)) {
            return MemoryScope.FAMILY_VISIBLE;
        }
        return MemoryScope.valueOf(value);
    }

    private static String structuredText(Object structured, String key) {
        if (structured instanceof Map<?, ?> map) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private static boolean archived(Map<String, Object> metadata) {
        return EntityStatus.ARCHIVED.name().equalsIgnoreCase(String.valueOf(metadata.get("status")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(Object value) {
        return value instanceof Map<?, ?> map
                ? new HashMap<>((Map<String, Object>) map)
                : new HashMap<>();
    }
}
