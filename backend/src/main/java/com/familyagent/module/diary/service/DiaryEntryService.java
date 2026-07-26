package com.familyagent.module.diary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.dto.CreateDiaryEntryRequest;
import com.familyagent.module.diary.dto.DiaryEntryMetadata;
import com.familyagent.module.diary.dto.UpdateDiaryEntryRequest;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.facade.UnifiedDiaryRecordFacade;
import com.familyagent.module.memory.service.MemoryIndexMetadataBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DiaryEntryService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 80;
    private static final int DEFAULT_PAGE_SIZE = 6;
    private static final int MAX_PAGE_SIZE = 20;
    private static final int SINGLE_ENTRY_MAX_CHARS = 300;
    private static final int MERGED_ENTRY_MAX_CHARS = 600;
    private static final int MAX_ENTRIES_PER_DAY = 10;
    private static final Set<String> VISIBILITIES = MemoryScope.diaryNames();
    private static final Set<String> ENTRY_TYPES = Set.of(
            "DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION");

    private final UnifiedDiaryRecordFacade diaryRecords;
    private final FamilyService familyService;
    private final DiaryMemorySyncSupport memorySyncSupport;

    @Transactional
    public DiaryEntry create(CreateDiaryEntryRequest request) {
        Long userId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(request.getFamilyId());

        if (diaryRecords.countTodayByUser(userId) >= MAX_ENTRIES_PER_DAY) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED,
                    "每天最多记录 " + MAX_ENTRIES_PER_DAY + " 条，请明天再记");
        }

        String visibility = normalizeVisibility(request.getVisibility());
        DiaryEntryMetadata inputMetadata = request.getMetadata();
        String diaryDate = DiaryEntryMetadataSupport.resolveDiaryDate(inputMetadata);
        String incomingTitle = firstLineOrBlank(blankToNull(request.getTitle()), request.getContent());
        String incomingContent = localCompress("", request.getContent().trim(), SINGLE_ENTRY_MAX_CHARS);
        Map<String, Object> requestMetadata = DiaryEntryMetadataSupport.build(
                inputMetadata,
                diaryDate,
                SINGLE_ENTRY_MAX_CHARS,
                MERGED_ENTRY_MAX_CHARS);
        DiaryEntry existing = findEligibleMergeCandidate(
                request, userId, visibility, diaryDate, inputMetadata);
        if (existing != null) {
            String mergedContent = localCompress(
                    existing.getRawText(),
                    incomingContent,
                    MERGED_ENTRY_MAX_CHARS);
            Map<String, Object> metadata = DiaryEntryMetadataSupport.merge(
                    existing.getMetadata(), requestMetadata);
            metadata.put("autoMerged", true);
            metadata.put("mergedCount", asInt(metadata.get("mergedCount"), 1) + 1);
            metadata.put("lastMergedAt", java.time.LocalDateTime.now().toString());
            existing.setRawText(mergedContent);
            existing.setStructured(buildStructured(
                    chooseEntryType(existing, request.getEntryType()),
                    chooseTitle(existing, request.getTitle(), diaryDate),
                    mergedContent));
            existing.setMood(blankToNull(request.getMood()) == null ? existing.getMood() : blankToNull(request.getMood()));
            existing.setTags(mergeTags(existing.getTags(), request.getTags()));
            existing.setMetadata(MemoryIndexMetadataBuilder.enrichDiary(
                    metadata,
                    existing.getRawText(),
                    String.valueOf(((Map<?, ?>) existing.getStructured()).get("entryType")),
                    existing.getMood(),
                    existing.getTags()));
            memorySyncSupport.sync(existing);
            return existing;
        }

        DiaryEntry entry = new DiaryEntry();
        entry.setUserId(userId);
        entry.setFamilyId(request.getFamilyId());
        entry.setRawText(incomingContent);
        entry.setStructured(buildStructured(request.getEntryType(), incomingTitle, incomingContent));
        entry.setMood(blankToNull(request.getMood()));
        entry.setTags(request.getTags() == null ? new String[0] : request.getTags().toArray(String[]::new));
        entry.setVisibility(visibility);
        entry.setPrivacyLevel(visibility);
        entry.setPermissionScope(Map.of());
        entry.setSource(DiaryEntryMetadataSupport.resolveEntrySource(inputMetadata));
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichDiary(
                requestMetadata,
                entry.getRawText(),
                String.valueOf(((Map<?, ?>) entry.getStructured()).get("entryType")),
                entry.getMood(),
                entry.getTags()));
        memorySyncSupport.create(entry);
        return entry;
    }

    public List<DiaryEntry> listFamilyEntries(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        return diaryRecords.findVisibleByFamily(
                familyId,
                CurrentUserGuard.currentUserId(),
                normalizeLimit(limit));
    }

    public PageResult<DiaryEntry> searchFamilyEntries(Long familyId, Long targetUserId, String keyword, int page, int pageSize) {
        familyService.checkMembership(familyId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        int normalizedPageSize = normalizePageSize(pageSize);
        String normalizedKeyword = normalizeKeyword(keyword);
        long total = diaryRecords.countVisibleByFamilySearch(familyId, viewerUserId, targetUserId, normalizedKeyword);
        long resolvedPage = resolvePage(page, normalizedPageSize, total);
        long offset = (resolvedPage - 1L) * normalizedPageSize;
        List<DiaryEntry> items = total == 0
                ? List.of()
                : diaryRecords.searchVisibleByFamily(
                        familyId,
                        viewerUserId,
                        targetUserId,
                        normalizedKeyword,
                        normalizedPageSize,
                        offset);
        return PageResult.of(items, resolvedPage, normalizedPageSize, total);
    }

    @Transactional
    public DiaryEntry update(Long id, UpdateDiaryEntryRequest request) {
        DiaryEntry entry = diaryRecords.findById(id);
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
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichDiary(
                DiaryEntryMetadataSupport.merge(entry.getMetadata(), request.getMetadata()),
                entry.getRawText(),
                String.valueOf(((Map<?, ?>) entry.getStructured()).get("entryType")),
                entry.getMood(),
                entry.getTags()));
        memorySyncSupport.sync(entry);
        return entry;
    }

    @Transactional
    public void archive(Long id) {
        DiaryEntry entry = diaryRecords.findById(id);
        if (entry == null || isArchived(entry)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        CurrentUserGuard.requireSelf(entry.getUserId());
        Map<String, Object> metadata = toMutableMap(entry.getMetadata());
        metadata.put("status", EntityStatus.ARCHIVED.name());
        entry.setMetadata(metadata);
        memorySyncSupport.sync(entry);
    }

    private static Map<String, Object> buildStructured(String entryType, String title, String content) {
        String trimmedContent = content.trim();
        Map<String, Object> structured = new HashMap<>();
        structured.put("entryType", normalizeEntryType(entryType));
        structured.put("title", firstLineOrBlank(blankToNull(title), trimmedContent));
        structured.put("summary", trimmedContent.substring(0, Math.min(120, trimmedContent.length())));
        return structured;
    }

    private DiaryEntry findEligibleMergeCandidate(
            CreateDiaryEntryRequest request,
            Long userId,
            String visibility,
            String diaryDate,
            DiaryEntryMetadata metadata) {
        if (!shouldAutoMerge(request, metadata)) {
            return null;
        }
        List<DiaryEntry> candidates = diaryRecords.findSameDayMergeCandidates(
                request.getFamilyId(),
                userId,
                visibility,
                diaryDate);
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static boolean shouldAutoMerge(CreateDiaryEntryRequest request, DiaryEntryMetadata metadata) {
        return DiaryEntryMetadataSupport.allowsAutoMerge(metadata)
                && "DAILY".equals(normalizeEntryType(request.getEntryType()));
    }

    private static String normalizeVisibility(String visibility) {
        String normalized = visibility == null ? MemoryScope.DEFAULT_DIARY.name() : visibility.trim().toUpperCase(Locale.ROOT);
        if (!VISIBILITIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日记可见范围不支持");
        }
        return normalized;
    }

    private static String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private static int normalizePageSize(int pageSize) {
        if (pageSize <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static long resolvePage(int page, int pageSize, long total) {
        long normalizedPage = Math.max(page, 1);
        if (total <= 0) {
            return normalizedPage;
        }
        long totalPages = (total + pageSize - 1L) / pageSize;
        return Math.min(normalizedPage, totalPages);
    }

    private static String chooseEntryType(DiaryEntry existing, String nextEntryType) {
        String normalizedNext = normalizeEntryType(nextEntryType);
        if (!"DAILY".equals(normalizedNext)) {
            return normalizedNext;
        }
        Object structured = existing.getStructured();
        if (structured instanceof Map<?, ?> map && map.get("entryType") != null) {
            return normalizeEntryType(String.valueOf(map.get("entryType")));
        }
        return normalizedNext;
    }

    private static String chooseTitle(DiaryEntry existing, String nextTitle, String diaryDate) {
        String title = blankToNull(nextTitle);
        if (title != null) {
            return title;
        }
        Object structured = existing.getStructured();
        if (structured instanceof Map<?, ?> map && map.get("title") != null) {
            return String.valueOf(map.get("title"));
        }
        return diaryDate + " 的记录";
    }

    private static String[] mergeTags(String[] currentTags, List<String> nextTags) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (currentTags != null) {
            Arrays.stream(currentTags)
                    .filter(tag -> tag != null && !tag.isBlank())
                    .forEach(tag -> result.add(tag.trim()));
        }
        if (nextTags != null) {
            nextTags.stream()
                    .filter(tag -> tag != null && !tag.isBlank())
                    .forEach(tag -> result.add(tag.trim()));
        }
        return result.stream().limit(10).toArray(String[]::new);
    }

    private static String localCompress(String currentContent, String incomingContent, int maxChars) {
        String current = normalizeText(currentContent);
        String incoming = normalizeText(incomingContent);
        String merged;
        if (current.isBlank()) {
            merged = incoming;
        } else if (incoming.isBlank() || current.contains(incoming)) {
            merged = current;
        } else {
            merged = current + "；" + incoming;
        }
        return truncate(merged, maxChars);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String truncate(String value, int maxChars) {
        String text = normalizeText(value);
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(1, maxChars - 1)).replaceAll("[，,；;。\\s]+$", "") + "…";
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
            return EntityStatus.ARCHIVED.name().equalsIgnoreCase(String.valueOf(map.get("status")));
        }
        return false;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstLineOrBlank(String preferredTitle, String content) {
        if (preferredTitle != null) {
            return preferredTitle;
        }
        return Arrays.stream(content.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .map(line -> line.substring(0, Math.min(120, line.length())))
                .orElse(null);
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMutableMap(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }
}
