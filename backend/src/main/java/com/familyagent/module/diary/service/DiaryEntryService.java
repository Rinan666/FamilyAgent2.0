package com.familyagent.module.diary.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.dto.CreateDiaryEntryRequest;
import com.familyagent.module.diary.dto.UpdateDiaryEntryRequest;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.service.MemoryEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DiaryEntryService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 80;
    private static final Set<String> VISIBILITIES = Set.of(
            "PRIVATE", "FAMILY_VISIBLE", "CARE_VISIBLE", "PARENT_VISIBLE", "LEGACY_VISIBLE");
    private static final Set<String> ENTRY_TYPES = Set.of(
            "DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION");

    private final DiaryEntryRepository diaryRepository;
    private final FamilyService familyService;
    private final MemoryEmbeddingService memoryEmbeddingService;

    @Transactional
    public DiaryEntry create(CreateDiaryEntryRequest request) {
        Long userId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(request.getFamilyId());

        DiaryEntry entry = new DiaryEntry();
        entry.setUserId(userId);
        entry.setFamilyId(request.getFamilyId());
        entry.setRawText(request.getContent().trim());
        entry.setStructured(buildStructured(request));
        entry.setMood(blankToNull(request.getMood()));
        entry.setTags(request.getTags() == null ? new String[0] : request.getTags().toArray(String[]::new));
        String visibility = normalizeVisibility(request.getVisibility());
        entry.setVisibility(visibility);
        entry.setPrivacyLevel(visibility);
        entry.setPermissionScope(Map.of());
        entry.setSource("USER_INPUT");
        entry.setMetadata(buildMetadata(request));
        diaryRepository.insert(entry);
        memoryEmbeddingService.indexDiaryAfterCommit(entry);
        return entry;
    }

    public List<DiaryEntry> listFamilyEntries(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        return diaryRepository.findVisibleByFamily(
                familyId,
                CurrentUserGuard.currentUserId(),
                normalizeLimit(limit));
    }

    @Transactional
    public DiaryEntry update(Long id, UpdateDiaryEntryRequest request) {
        DiaryEntry entry = diaryRepository.selectById(id);
        if (entry == null || isArchived(entry)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        CurrentUserGuard.requireSelf(entry.getUserId());
        familyService.checkMembership(entry.getFamilyId());

        entry.setRawText(request.getContent().trim());
        entry.setStructured(buildStructured(request.getEntryType(), request.getTitle(), request.getContent()));
        entry.setMood(blankToNull(request.getMood()));
        entry.setTags(request.getTags() == null ? new String[0] : request.getTags().toArray(String[]::new));
        String visibility = normalizeVisibility(request.getVisibility());
        entry.setVisibility(visibility);
        entry.setPrivacyLevel(visibility);
        entry.setMetadata(mergeMetadata(entry.getMetadata(), request.getMetadata()));
        diaryRepository.updateById(entry);
        memoryEmbeddingService.indexDiaryAfterCommit(entry);
        return entry;
    }

    @Transactional
    public void archive(Long id) {
        DiaryEntry entry = diaryRepository.selectById(id);
        if (entry == null || isArchived(entry)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        CurrentUserGuard.requireSelf(entry.getUserId());
        Map<String, Object> metadata = toMutableMap(entry.getMetadata());
        metadata.put("status", "ARCHIVED");
        entry.setMetadata(metadata);
        diaryRepository.updateById(entry);
    }

    private static Map<String, Object> buildStructured(CreateDiaryEntryRequest request) {
        return buildStructured(request.getEntryType(), request.getTitle(), request.getContent());
    }

    private static Map<String, Object> buildStructured(String entryType, String title, String content) {
        String trimmedContent = content.trim();
        Map<String, Object> structured = new HashMap<>();
        structured.put("entryType", normalizeEntryType(entryType));
        structured.put("title", blankToNull(title));
        structured.put("summary", trimmedContent.substring(0, Math.min(120, trimmedContent.length())));
        return structured;
    }

    private static Map<String, Object> buildMetadata(CreateDiaryEntryRequest request) {
        Map<String, Object> metadata = request.getMetadata() == null ? new HashMap<>() : new HashMap<>(request.getMetadata());
        metadata.putIfAbsent("status", "ACTIVE");
        metadata.put("sourceModule", "DIARY");
        return metadata;
    }

    private static Map<String, Object> mergeMetadata(Object currentMetadata, Map<String, Object> nextMetadata) {
        Map<String, Object> metadata = toMutableMap(currentMetadata);
        if (nextMetadata != null) {
            metadata.putAll(nextMetadata);
        }
        metadata.putIfAbsent("status", "ACTIVE");
        metadata.put("sourceModule", "DIARY");
        return metadata;
    }

    private static String normalizeVisibility(String visibility) {
        String normalized = visibility == null ? "PRIVATE" : visibility.trim().toUpperCase(Locale.ROOT);
        if (!VISIBILITIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日记可见范围不支持");
        }
        return normalized;
    }

    private static String normalizeEntryType(String entryType) {
        String normalized = entryType == null ? "DAILY" : entryType.trim().toUpperCase(Locale.ROOT);
        return ENTRY_TYPES.contains(normalized) ? normalized : "DAILY";
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private static boolean isArchived(DiaryEntry entry) {
        Object metadata = entry.getMetadata();
        if (metadata instanceof Map<?, ?> map) {
            return "ARCHIVED".equalsIgnoreCase(String.valueOf(map.get("status")));
        }
        return false;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMutableMap(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }
}
