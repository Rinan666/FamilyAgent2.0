package com.familyagent.module.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.dto.CreateMemoryEntryRequest;
import com.familyagent.module.memory.dto.MemoryVoteStats;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.entity.MemoryEntryVote;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemoryService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;
    private static final int DEFAULT_PAGE_SIZE = 6;
    private static final int MAX_PAGE_SIZE = 20;
    private static final Set<String> FAMILY_MEMORY_TYPES = Set.of(
            "FAMILY_STORY", "ELDER_ADVICE", "HEALTH_REMINDER", "GROWTH_RISK", "VALUE", "PLAN");
    private static final Set<String> FAMILY_MEMORY_SCOPES = Set.of(
            "PRIVATE", "PARENT_VISIBLE", "CARE_VISIBLE", "FAMILY_VISIBLE");
    private static final Set<String> MANUAL_HERITAGE_SOURCES = Set.of(
            "HERITAGE_ENTRY", "HERITAGE_INTERVIEW", "HERITAGE_ATOM");
    private static final int SIMILAR_MEMORY_SCAN_LIMIT = 30;

    private final MemoryEntryRepository memoryRepository;
    private final MemoryEntryVoteRepository voteRepository;
    private final FamilyService familyService;
    private final MemoryEmbeddingService memoryEmbeddingService;

    @Transactional
    public MemoryEntry createMemory(CreateMemoryEntryRequest request) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "学习记忆功能已下线，请使用家族记忆、每日记录或成长观察。");
    }

    public List<MemoryEntry> listMyMemories(int limit) {
        return List.of();
    }

    @Transactional
    public MemoryEntry createFamilyMemory(CreateFamilyMemoryRequest request) {
        Long userId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(request.getFamilyId());

        Map<String, Object> metadata = buildFamilyMemoryMetadata(request);
        validateManualHeritageSaveJudge(request.getMetadata());
        Object sourceDiaryId = metadata.get("sourceDiaryId");
        if ("DIARY_PROMOTION".equals(metadata.get("source")) && sourceDiaryId != null) {
            MemoryEntry existing = memoryRepository.findActiveBySourceDiaryId(
                    request.getFamilyId(),
                    String.valueOf(sourceDiaryId));
            if (existing != null) {
                return existing;
            }
        }

        MemoryEntry similar = findSimilarFamilyMemory(request, metadata, userId);
        if (similar != null) {
            return mergeFamilyMemory(similar, request, metadata, userId);
        }

        MemoryEntry entry = new MemoryEntry();
        entry.setUserId(userId);
        entry.setFamilyId(request.getFamilyId());
        entry.setType(normalizeFamilyMemoryType(request.getType()));
        entry.setScope(normalizeFamilyMemoryScope(request.getScope()));
        entry.setContent(request.getContent().trim());
        entry.setSummary(blankToNull(request.getSummary()));
        entry.setImportance(clamp(request.getImportance() == null ? 3 : request.getImportance(), 1, 5));
        entry.setConfidence(BigDecimal.valueOf(0.85));
        entry.setStatus("ACTIVE");
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichFamilyMemory(
                metadata,
                entry.getContent(),
                entry.getSummary(),
                entry.getType(),
                entry.getImportance()));
        memoryRepository.insert(entry);
        memoryEmbeddingService.indexMemoryAfterCommit(entry);
        return entry;
    }

    private MemoryEntry findSimilarFamilyMemory(
            CreateFamilyMemoryRequest request,
            Map<String, Object> incomingMetadata,
            Long viewerUserId) {
        String incomingType = normalizeFamilyMemoryType(request.getType());
        String incomingScope = normalizeFamilyMemoryScope(request.getScope());
        List<MemoryEntry> candidates = memoryRepository.findActiveFamilyMemories(
                request.getFamilyId(),
                viewerUserId,
                SIMILAR_MEMORY_SCAN_LIMIT);
        return candidates.stream()
                .filter(candidate -> incomingType.equals(candidate.getType()))
                .filter(candidate -> incomingScope.equals(candidate.getScope()))
                .filter(candidate -> similarityScore(candidate, request, incomingMetadata) >= 7)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public MemoryEntry mergeFamilyMemory(
            MemoryEntry existing,
            CreateFamilyMemoryRequest request,
            Map<String, Object> incomingMetadata,
            Long viewerUserId) {
        Map<String, Object> metadata = toMutableMap(existing.getMetadata());
        Map<String, Object> incomingCard = request.getMemoryCard() == null ? Map.of() : request.getMemoryCard();
        Map<String, Object> existingCard = mapValue(metadata.get("memoryCard"));

        String mergedContent = mergeText(existing.getContent(), request.getContent(), 1200);
        String mergedSummary = firstNonBlank(
                asText(incomingCard.get("summary")),
                request.getSummary(),
                existing.getSummary(),
                mergedContent);
        Map<String, Object> mergedCard = mergeMemoryCard(existingCard, incomingCard, mergedSummary, mergedContent);

        metadata.put("memoryCard", mergedCard);
        metadata.put("source", "MERGED_HERITAGE");
        metadata.put("mergedAt", java.time.LocalDateTime.now().toString());
        metadata.put("mergedReason", "主题相近或解决同一类问题，自动合并为更凝练的家族智慧。");
        metadata.put("mergedSourceCount", intValue(metadata.get("mergedSourceCount")) + 1);
        metadata.put("lastMergedSource", Map.of(
                "source", String.valueOf(incomingMetadata.getOrDefault("source", "HERITAGE_ENTRY")),
                "scenario", String.valueOf(incomingMetadata.getOrDefault("scenario", "")),
                "preview", request.getContent().trim().substring(0, Math.min(80, request.getContent().trim().length()))));

        existing.setContent(mergedContent);
        existing.setSummary(mergedSummary.length() > 200 ? mergedSummary.substring(0, 200) : mergedSummary);
        existing.setImportance(Math.max(
                existing.getImportance() == null ? 3 : existing.getImportance(),
                clamp(request.getImportance() == null ? 3 : request.getImportance(), 1, 5)));
        existing.setMetadata(MemoryIndexMetadataBuilder.enrichFamilyMemory(
                metadata,
                existing.getContent(),
                existing.getSummary(),
                existing.getType(),
                existing.getImportance()));
        memoryRepository.updateById(existing);
        memoryEmbeddingService.indexMemoryAfterCommit(existing);
        attachVoteStats(existing, viewerUserId);
        return existing;
    }

    public List<MemoryEntry> listFamilyMemories(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        List<MemoryEntry> entries = memoryRepository.findActiveFamilyMemories(
                familyId,
                viewerUserId,
                normalizeLimit(limit));
        entries.forEach(entry -> attachVoteStats(entry, viewerUserId));
        return entries.stream()
                .sorted((left, right) -> {
                    int byScore = Integer.compare(
                            voteStatsFromMetadata(right).getVoteScore(),
                            voteStatsFromMetadata(left).getVoteScore());
                    if (byScore != 0) {
                        return byScore;
                    }
                    return 0;
                })
                .toList();
    }

    public PageResult<MemoryEntry> searchFamilyMemories(Long familyId, Long targetUserId, String keyword, int page, int pageSize) {
        familyService.checkMembership(familyId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        int normalizedPageSize = normalizePageSize(pageSize);
        String normalizedKeyword = normalizeKeyword(keyword);
        long total = memoryRepository.countActiveFamilyMemoriesSearch(familyId, viewerUserId, targetUserId, normalizedKeyword);
        long resolvedPage = resolvePage(page, normalizedPageSize, total);
        long offset = (resolvedPage - 1L) * normalizedPageSize;
        List<MemoryEntry> items = total == 0
                ? List.of()
                : memoryRepository.searchActiveFamilyMemories(
                        familyId,
                        viewerUserId,
                        targetUserId,
                        normalizedKeyword,
                        normalizedPageSize,
                        offset);
        items.forEach(entry -> attachVoteStats(entry, viewerUserId));
        return PageResult.of(items, resolvedPage, normalizedPageSize, total);
    }

    @Transactional
    public MemoryEntry voteFamilyMemory(Long memoryId, String voteType) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        String normalizedVote = normalizeVoteType(voteType);
        MemoryEntry entry = memoryRepository.selectById(memoryId);
        if (entry == null || !"ACTIVE".equals(entry.getStatus()) || entry.getFamilyId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!FAMILY_MEMORY_TYPES.contains(entry.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有家族经验可以投票");
        }
        familyService.checkMembership(entry.getFamilyId());
        MemoryEntry visibleEntry = memoryRepository.findVisibleFamilyMemoryById(entry.getFamilyId(), memoryId, viewerUserId);
        if (visibleEntry == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权对不可见的家族经验投票");
        }

        MemoryEntryVote existing = voteRepository.selectOne(new LambdaQueryWrapper<MemoryEntryVote>()
                .eq(MemoryEntryVote::getMemoryId, memoryId)
                .eq(MemoryEntryVote::getUserId, viewerUserId)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new MemoryEntryVote();
            existing.setMemoryId(memoryId);
            existing.setFamilyId(entry.getFamilyId());
            existing.setUserId(viewerUserId);
            existing.setVoteType(normalizedVote);
            voteRepository.insert(existing);
        } else if (!normalizedVote.equals(existing.getVoteType())) {
            existing.setVoteType(normalizedVote);
            voteRepository.updateById(existing);
        }

        attachVoteStats(visibleEntry, viewerUserId);
        return visibleEntry;
    }

    public List<MemoryEntry> recall(String subject, int limit) {
        return List.of();
    }

    @Transactional
    public void archiveMemory(Long id) {
        MemoryEntry entry = memoryRepository.selectById(id);
        if (entry == null || !"ACTIVE".equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        CurrentUserGuard.requireSelf(entry.getUserId());
        entry.setStatus("ARCHIVED");
        memoryRepository.updateById(entry);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
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

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalizeFamilyMemoryType(String type) {
        String normalized = type == null ? "ELDER_ADVICE" : type.trim().toUpperCase(Locale.ROOT);
        if (!FAMILY_MEMORY_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "家族经验类型不支持");
        }
        return normalized;
    }

    private static String normalizeFamilyMemoryScope(String scope) {
        String normalized = scope == null ? "FAMILY_VISIBLE" : scope.trim().toUpperCase(Locale.ROOT);
        if (!FAMILY_MEMORY_SCOPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可见范围不支持");
        }
        return normalized;
    }

    private static String normalizeVoteType(String voteType) {
        String normalized = voteType == null ? "" : voteType.trim().toUpperCase(Locale.ROOT);
        if (!"UP".equals(normalized) && !"DOWN".equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "投票只能是 UP 或 DOWN");
        }
        return normalized;
    }

    private void attachVoteStats(MemoryEntry entry, Long viewerUserId) {
        MemoryVoteStats stats = voteRepository.statsByMemoryId(entry.getId(), viewerUserId);
        if (stats == null) {
            stats = new MemoryVoteStats(entry.getId(), 0, 0, 0, 1.0, null);
        }
        Map<String, Object> metadata = toMutableMap(entry.getMetadata());
        metadata.put("voteStats", Map.of(
                "memoryId", entry.getId(),
                "upVotes", stats.getUpVotes(),
                "downVotes", stats.getDownVotes(),
                "voteScore", stats.getVoteScore(),
                "consensusWeight", stats.getConsensusWeight(),
                "myVote", stats.getMyVote() == null ? "" : stats.getMyVote()));
        entry.setMetadata(metadata);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMutableMap(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static MemoryVoteStats voteStatsFromMetadata(MemoryEntry entry) {
        if (entry.getMetadata() instanceof Map<?, ?> metadata && metadata.get("voteStats") instanceof Map<?, ?> stats) {
            int upVotes = asInt(stats.get("upVotes"));
            int downVotes = asInt(stats.get("downVotes"));
            int voteScore = asInt(stats.get("voteScore"));
            double consensusWeight = stats.get("consensusWeight") instanceof Number number ? number.doubleValue() : 1.0;
            Object myVote = stats.get("myVote");
            return new MemoryVoteStats(entry.getId(), upVotes, downVotes, voteScore, consensusWeight, myVote == null ? null : String.valueOf(myVote));
        }
        return new MemoryVoteStats(entry.getId(), 0, 0, 0, 1.0, null);
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static int similarityScore(
            MemoryEntry candidate,
            CreateFamilyMemoryRequest request,
            Map<String, Object> incomingMetadata) {
        Map<String, Object> candidateMetadata = toMutableMap(candidate.getMetadata());
        Map<String, Object> candidateCard = mapValue(candidateMetadata.get("memoryCard"));
        Map<String, Object> incomingCard = request.getMemoryCard() == null ? Map.of() : request.getMemoryCard();
        int score = 0;

        if (sameNonBlank(asText(candidateMetadata.get("scenario")), asText(incomingMetadata.get("scenario")))) {
            score += 4;
        }
        if (sameNonBlank(asText(candidateMetadata.get("target")), asText(incomingMetadata.get("target")))) {
            score += 3;
        }
        if (sameNonBlank(asText(candidateCard.get("theme")), asText(incomingCard.get("theme")))) {
            score += 3;
        }
        if (sameNonBlank(asText(candidateCard.get("title")), asText(incomingCard.get("title")))) {
            score += 2;
        }

        Set<String> leftSignals = memorySignals(
                candidate.getType(),
                candidate.getContent(),
                candidate.getSummary(),
                candidateMetadata,
                candidateCard);
        Set<String> rightSignals = memorySignals(
                request.getType(),
                request.getContent(),
                request.getSummary(),
                incomingMetadata,
                incomingCard);
        leftSignals.retainAll(rightSignals);
        score += Math.min(6, leftSignals.size());
        score += textOverlapScore(candidate.getContent() + " " + candidate.getSummary(), request.getContent());
        return score;
    }

    private static Map<String, Object> mergeMemoryCard(
            Map<String, Object> existingCard,
            Map<String, Object> incomingCard,
            String mergedSummary,
            String mergedContent) {
        Map<String, Object> card = new HashMap<>(existingCard);
        card.put("title", firstNonBlank(asText(incomingCard.get("title")), asText(existingCard.get("title")), "经验沉淀"));
        card.put("theme", firstNonBlank(asText(incomingCard.get("theme")), asText(existingCard.get("theme")), "家族智慧"));
        card.put("summary", mergedSummary);
        card.put("motto", firstNonBlank(
                asText(incomingCard.get("motto")),
                asText(existingCard.get("motto")),
                localMotto(mergedContent)));
        card.put("risk_points", mergeList(existingCard.get("risk_points"), incomingCard.get("risk_points"), 4));
        card.put("action_suggestions", mergeList(existingCard.get("action_suggestions"), incomingCard.get("action_suggestions"), 5));
        card.put("suitable_for", mergeList(existingCard.get("suitable_for"), incomingCard.get("suitable_for"), 4));
        card.put("sensitivity", strongestSensitivity(asText(existingCard.get("sensitivity")), asText(incomingCard.get("sensitivity"))));
        card.put("safety_note", firstNonBlank(
                asText(incomingCard.get("safety_note")),
                asText(existingCard.get("safety_note")),
                "这是一条家族经验整理，不构成专业诊断。"));
        card.put("merged", true);
        return card;
    }

    private static Set<String> memorySignals(
            String type,
            String content,
            String summary,
            Map<String, Object> metadata,
            Map<String, Object> card) {
        Set<String> signals = new LinkedHashSet<>();
        addSignal(signals, type);
        addSignal(signals, asText(metadata.get("scenario")));
        addSignal(signals, asText(metadata.get("target")));
        addSignal(signals, asText(card.get("theme")));
        addSignal(signals, asText(card.get("title")));
        String text = normalizeText(content + " " + summary + " " + asText(card.get("summary")) + " " + asText(card.get("motto")));
        for (String keyword : List.of(
                "牙", "视力", "体态", "睡眠", "运动", "屏幕", "健康", "情绪", "沟通",
                "选择", "志愿", "专业", "工作", "考研", "规矩", "家风", "风险", "教训")) {
            if (text.contains(keyword)) {
                signals.add(keyword);
            }
        }
        for (String token : text.split("[^\\p{IsHan}\\p{Alnum}]+")) {
            if (token.length() >= 2 && token.length() <= 12) {
                signals.add(token);
            }
        }
        return signals;
    }

    private static void addSignal(Set<String> signals, String value) {
        String text = normalizeText(value);
        if (!text.isBlank()) {
            signals.add(text);
        }
    }

    private static int textOverlapScore(String left, String right) {
        String a = normalizeText(left);
        String b = normalizeText(right);
        if (a.isBlank() || b.isBlank()) {
            return 0;
        }
        if (a.contains(b) || b.contains(a)) {
            return 4;
        }
        Set<String> pieces = new LinkedHashSet<>();
        for (int i = 0; i + 2 <= b.length(); i += 2) {
            String piece = b.substring(i, i + 2);
            if (piece.chars().anyMatch(Character::isLetterOrDigit)) {
                pieces.add(piece);
            }
        }
        int hits = 0;
        for (String piece : pieces) {
            if (a.contains(piece)) {
                hits += 1;
            }
        }
        return Math.min(4, hits / 2);
    }

    private static String mergeText(String existing, String incoming, int maxLength) {
        List<String> parts = new ArrayList<>();
        for (String value : List.of(existing, incoming)) {
            String text = value == null ? "" : value.trim();
            if (!text.isBlank() && parts.stream().noneMatch(text::equals)) {
                parts.add(text);
            }
        }
        String merged = String.join("\n\n补充：", parts);
        if (merged.length() <= maxLength) {
            return merged;
        }
        return merged.substring(0, Math.max(0, maxLength - 1)).strip() + "…";
    }

    private static List<String> mergeList(Object left, Object right, int limit) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectList(values, left);
        collectList(values, right);
        return values.stream().limit(limit).toList();
    }

    private static void collectList(Set<String> values, Object value) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            String text = asText(item);
            if (!text.isBlank()) {
                values.add(text.length() > 100 ? text.substring(0, 100) : text);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }

    private static String strongestSensitivity(String left, String right) {
        List<String> order = List.of("LOW", "MEDIUM", "HIGH");
        String a = left == null ? "LOW" : left.trim().toUpperCase(Locale.ROOT);
        String b = right == null ? "LOW" : right.trim().toUpperCase(Locale.ROOT);
        int ai = order.indexOf(a);
        int bi = order.indexOf(b);
        return order.get(Math.max(Math.max(ai, 0), Math.max(bi, 0)));
    }

    private static boolean sameNonBlank(String left, String right) {
        String a = normalizeText(left);
        String b = normalizeText(right);
        return !a.isBlank() && a.equals(b);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String normalizeText(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    private static String localMotto(String content) {
        String text = content == null ? "" : content;
        if (text.matches(".*(牙|视力|体态|睡眠|运动|健康).*")) {
            return "小患早察，久安可期";
        }
        if (text.matches(".*(选择|决定|志愿|专业|工作|考研).*")) {
            return "大事慢决，远路慎行";
        }
        if (text.matches(".*(沟通|争吵|理解|亲子|家人).*")) {
            return "言有余地，心有回声";
        }
        return "事经一回，智留一寸";
    }

    private static void validateManualHeritageSaveJudge(Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }
        Object source = metadata.get("source");
        if (!MANUAL_HERITAGE_SOURCES.contains(String.valueOf(source))) {
            return;
        }
        Object saveJudgeValue = metadata.get("saveJudge");
        if (!(saveJudgeValue instanceof Map<?, ?> saveJudge)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先完成家族经验保存价值判断");
        }
        Object shouldSave = saveJudge.get("shouldSave");
        if (!(shouldSave instanceof Boolean allowed) || !allowed) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先完成家族经验保存价值判断");
        }
    }

    private static Map<String, Object> buildFamilyMemoryMetadata(CreateFamilyMemoryRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        if (request.getMemoryCard() != null) {
            metadata.put("memoryCard", request.getMemoryCard());
        }
        metadata.putIfAbsent("source", "HERITAGE_ENTRY");
        return metadata;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
