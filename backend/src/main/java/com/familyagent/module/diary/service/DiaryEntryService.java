package com.familyagent.module.diary.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.diary.dto.CreateDiaryEntryRequest;
import com.familyagent.module.diary.dto.UpdateDiaryEntryRequest;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.service.MemoryEmbeddingService;
import com.familyagent.module.memory.service.MemoryIndexMetadataBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
    private static final String MANUAL_DIARY_SOURCE = "DIARY_MANUAL";
    private static final String MERGE_POLICY = "MANUAL_SELF_SINGLE_CANDIDATE";
    private static final Set<String> VISIBILITIES = MemoryScope.diaryNames();
    private static final Set<String> ENTRY_TYPES = Set.of(
            "DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION");

    private final DiaryEntryRepository diaryRepository;
    private final FamilyService familyService;
    private final MemoryEmbeddingService memoryEmbeddingService;
    private final AIServiceClient aiServiceClient;

    @Transactional
    public DiaryEntry create(CreateDiaryEntryRequest request) {
        Long userId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(request.getFamilyId());
        String visibility = normalizeVisibility(request.getVisibility());
        String diaryDate = resolveDiaryDate(request);
        String incomingContent = compressDiaryContent("", request.getContent().trim(), SINGLE_ENTRY_MAX_CHARS, diaryDate);
        Map<String, Object> requestMetadata = buildMetadata(request, diaryDate);
        DiaryEntry existing = findEligibleMergeCandidate(request, userId, visibility, diaryDate, requestMetadata);
        if (existing != null) {
            String mergedContent = compressDiaryContent(
                    existing.getRawText(),
                    incomingContent,
                    MERGED_ENTRY_MAX_CHARS,
                    diaryDate);
            Map<String, Object> metadata = mergeMetadata(existing.getMetadata(), requestMetadata);
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
            diaryRepository.updateById(existing);
            memoryEmbeddingService.indexDiaryAfterCommit(existing);
            return existing;
        }

        DiaryEntry entry = new DiaryEntry();
        entry.setUserId(userId);
        entry.setFamilyId(request.getFamilyId());
        entry.setRawText(incomingContent);
        entry.setStructured(buildStructured(request.getEntryType(), request.getTitle(), incomingContent));
        entry.setMood(blankToNull(request.getMood()));
        entry.setTags(request.getTags() == null ? new String[0] : request.getTags().toArray(String[]::new));
        entry.setVisibility(visibility);
        entry.setPrivacyLevel(visibility);
        entry.setPermissionScope(Map.of());
        entry.setSource(resolveEntrySource(requestMetadata));
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichDiary(
                requestMetadata,
                entry.getRawText(),
                String.valueOf(((Map<?, ?>) entry.getStructured()).get("entryType")),
                entry.getMood(),
                entry.getTags()));
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

    public PageResult<DiaryEntry> searchFamilyEntries(Long familyId, Long targetUserId, String keyword, int page, int pageSize) {
        familyService.checkMembership(familyId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        int normalizedPageSize = normalizePageSize(pageSize);
        String normalizedKeyword = normalizeKeyword(keyword);
        long total = diaryRepository.countVisibleByFamilySearch(familyId, viewerUserId, targetUserId, normalizedKeyword);
        long resolvedPage = resolvePage(page, normalizedPageSize, total);
        long offset = (resolvedPage - 1L) * normalizedPageSize;
        List<DiaryEntry> items = total == 0
                ? List.of()
                : diaryRepository.searchVisibleByFamily(
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
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichDiary(
                mergeMetadata(entry.getMetadata(), request.getMetadata()),
                entry.getRawText(),
                String.valueOf(((Map<?, ?>) entry.getStructured()).get("entryType")),
                entry.getMood(),
                entry.getTags()));
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
        metadata.put("status", EntityStatus.ARCHIVED.name());
        entry.setMetadata(metadata);
        diaryRepository.updateById(entry);
    }

    private static Map<String, Object> buildStructured(String entryType, String title, String content) {
        String trimmedContent = content.trim();
        Map<String, Object> structured = new HashMap<>();
        structured.put("entryType", normalizeEntryType(entryType));
        structured.put("title", blankToNull(title));
        structured.put("summary", trimmedContent.substring(0, Math.min(120, trimmedContent.length())));
        return structured;
    }

    private static Map<String, Object> buildMetadata(CreateDiaryEntryRequest request, String diaryDate) {
        Map<String, Object> metadata = request.getMetadata() == null ? new HashMap<>() : new HashMap<>(request.getMetadata());
        metadata.putIfAbsent("status", EntityStatus.ACTIVE.name());
        metadata.put("sourceModule", "DIARY");
        metadata.put("diaryDate", diaryDate);
        metadata.putIfAbsent("mergePolicy", MERGE_POLICY);
        metadata.putIfAbsent("source", MANUAL_DIARY_SOURCE);
        metadata.putIfAbsent("singleMaxChars", SINGLE_ENTRY_MAX_CHARS);
        metadata.putIfAbsent("mergedMaxChars", MERGED_ENTRY_MAX_CHARS);
        return metadata;
    }

    private DiaryEntry findEligibleMergeCandidate(
            CreateDiaryEntryRequest request,
            Long userId,
            String visibility,
            String diaryDate,
            Map<String, Object> metadata) {
        if (!shouldAutoMerge(request, metadata)) {
            return null;
        }
        List<DiaryEntry> candidates = diaryRepository.findSameDayMergeCandidates(
                request.getFamilyId(),
                userId,
                visibility,
                diaryDate);
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static boolean shouldAutoMerge(CreateDiaryEntryRequest request, Map<String, Object> metadata) {
        if (hasRelatedUser(metadata)) {
            return false;
        }
        if (!MANUAL_DIARY_SOURCE.equals(resolveEntrySource(metadata))) {
            return false;
        }
        return "DAILY".equals(normalizeEntryType(request.getEntryType()));
    }

    private static boolean hasRelatedUser(Map<String, Object> metadata) {
        Object relatedUserId = metadata == null ? null : metadata.get("relatedUserId");
        if (relatedUserId == null) {
            return false;
        }
        String text = String.valueOf(relatedUserId).trim();
        return !text.isEmpty() && !"0".equals(text);
    }

    private static String resolveEntrySource(Map<String, Object> metadata) {
        Object source = metadata == null ? null : metadata.get("source");
        String normalized = source == null ? "" : String.valueOf(source).trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? MANUAL_DIARY_SOURCE : normalized;
    }

    private static Map<String, Object> mergeMetadata(Object currentMetadata, Map<String, Object> nextMetadata) {
        Map<String, Object> metadata = toMutableMap(currentMetadata);
        if (nextMetadata != null) {
            metadata.putAll(nextMetadata);
        }
        metadata.putIfAbsent("status", EntityStatus.ACTIVE.name());
        metadata.put("sourceModule", "DIARY");
        return metadata;
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

    private String compressDiaryContent(String currentContent, String incomingContent, int maxChars, String diaryDate) {
        String local = localCompress(currentContent, incomingContent, maxChars);
        if (local.length() <= maxChars && (currentContent == null || currentContent.isBlank())) {
            return local;
        }
        try {
            Map<String, Object> response = aiServiceClient.compressDiary(Map.of(
                    "current_content", currentContent == null ? "" : currentContent,
                    "incoming_content", incomingContent == null ? "" : incomingContent,
                    "max_chars", maxChars,
                    "diary_date", diaryDate
            ), currentAuthorization());
            Object data = response == null ? null : response.get("data");
            if (data instanceof Map<?, ?> map) {
                Object content = map.get("content");
                if (content instanceof String text && !text.isBlank()) {
                    return truncate(text.trim(), maxChars);
                }
            }
        } catch (Exception ignored) {
            // Best effort; local compression keeps the save path reliable.
        }
        return local;
    }

    private static String currentAuthorization() {
        try {
            String token = StpUtil.getTokenValue();
            return token == null ? "" : token;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String resolveDiaryDate(CreateDiaryEntryRequest request) {
        Object eventAt = request.getMetadata() == null ? null : firstNonNull(
                request.getMetadata().get("eventAt"),
                request.getMetadata().get("recordedAt"),
                request.getMetadata().get("savedFromFamilyChatAt"));
        LocalDate date = parseDate(eventAt);
        return (date == null ? LocalDate.now() : date).toString();
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.length() >= 10) {
            try {
                return LocalDate.parse(text.substring(0, 10));
            } catch (DateTimeParseException ignored) {
                // Try ISO date-time below.
            }
        }
        try {
            return OffsetDateTime.parse(text).toLocalDate();
        } catch (DateTimeParseException ignored) {
            return null;
        }
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
            return "ARCHIVED".equalsIgnoreCase(String.valueOf(map.get("status")));
        }
        return false;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
