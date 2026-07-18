package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryLibraryMetadataSource;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MemoryLibraryCommandSupport {

    private MemoryLibraryCommandSupport() {
    }

    static String requiredBody(String body) {
        String text = MemoryLibrarySupport.blankToNull(body);
        if (text == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Memory content cannot be blank");
        }
        return text;
    }

    static String normalize(String value, String fallback, Set<String> allowed, String errorMessage) {
        String normalized = MemoryLibrarySupport.blankToNull(value);
        normalized = normalized == null ? fallback : normalized.toUpperCase(Locale.ROOT);
        if (normalized == null || !allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, errorMessage);
        }
        return normalized;
    }

    static String[] normalizedTags(List<String> tags) {
        if (tags == null) {
            return new String[0];
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .limit(10)
                .toArray(String[]::new);
    }

    static Map<String, Object> diaryStructured(String entryType, String title, String content) {
        Map<String, Object> structured = new HashMap<>();
        structured.put("entryType", entryType);
        structured.put("title", MemoryLibrarySupport.blankToNull(title));
        structured.put("summary", summaryFrom(title, content));
        return structured;
    }

    static Map<String, Object> editMetadata(Object metadata) {
        Map<String, Object> next = MemoryLibrarySupport.mutableMap(metadata);
        next.put("lastEditedBy", CurrentUserGuard.currentUserId());
        next.put("lastEditedAt", LocalDateTime.now().toString());
        next.put("editSource", MemoryLibraryMetadataSource.MEMORY_LIBRARY_MAINTENANCE.name());
        return next;
    }

    static String summaryFrom(String title, String body) {
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

    static boolean isArchivedMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return EntityStatus.ARCHIVED.name().equalsIgnoreCase(String.valueOf(map.get("status")));
        }
        return false;
    }
}
