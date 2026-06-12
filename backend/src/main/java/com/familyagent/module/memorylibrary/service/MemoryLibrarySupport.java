package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.common.security.CurrentUserGuard;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Package-private static utilities shared across MemoryLibrary* services. */
class MemoryLibrarySupport {

    private MemoryLibrarySupport() {}

    static ParsedItemId parseItemId(String itemId) {
        if (itemId == null || itemId.isBlank() || !itemId.contains("-")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "记忆 ID 不合法");
        }
        String[] parts = itemId.split("-", 2);
        try {
            return new ParsedItemId(parts[0], Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "记忆 ID 不合法");
        }
    }

    static void ensureCreatorOrFamilyOwner(FamilyService familyService, Long familyId, Long creatorUserId, String message) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        if (viewerUserId.equals(creatorUserId)) return;
        try {
            familyService.checkOwner(familyId);
            return;
        } catch (BusinessException ignored) {
            // fall through
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, message);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> mutableMap(Object metadata) {
        if (metadata instanceof Map<?, ?> map) return new HashMap<>((Map<String, Object>) map);
        return new HashMap<>();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    static List<String> searchTerms(String keyword) {
        String normalized = blankToNull(keyword);
        if (normalized == null) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String token : normalized.toLowerCase(Locale.ROOT).split("\\s+")) {
            String trimmed = token.trim();
            if (trimmed.length() >= 2) {
                terms.add(trimmed);
            }
            if (terms.size() >= 6) {
                break;
            }
        }
        if (terms.isEmpty()) {
            terms.add(normalized.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(terms);
    }

    static String previewText(String value, int maxLength) {
        String text = asText(value);
        return text.length() <= maxLength ? text : text.substring(0, Math.max(0, maxLength - 1)).strip() + "…";
    }

    static String truncateText(String value, int maxLength) {
        String text = asText(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    record ParsedItemId(String prefix, Long id) {}
}
