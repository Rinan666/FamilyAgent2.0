package com.familyagent.module.memory.service;

import com.familyagent.common.constant.HeritageSource;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 家族经验相似度判断与合并逻辑，从 MemoryService 拆出。
 */
@Component
@RequiredArgsConstructor
public class MemoryMergeService {

    private static final int SIMILAR_MEMORY_SCAN_LIMIT = 30;

    private final MemoryEntryRepository memoryRepository;
    private final MemoryEmbeddingService memoryEmbeddingService;
    private final MemoryVoteService memoryVoteService;

    public MemoryEntry findSimilar(
            CreateFamilyMemoryRequest request,
            Map<String, Object> incomingMetadata,
            Long viewerUserId,
            String incomingType,
            String incomingScope) {
        List<MemoryEntry> candidates = memoryRepository.findActiveFamilyMemories(
                request.getFamilyId(), viewerUserId, SIMILAR_MEMORY_SCAN_LIMIT);
        return candidates.stream()
                .filter(c -> incomingType.equals(c.getType()))
                .filter(c -> incomingScope.equals(c.getScope()))
                .filter(c -> similarityScore(c, request, incomingMetadata) >= 7)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public MemoryEntry merge(
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
                "source", String.valueOf(incomingMetadata.getOrDefault("source", HeritageSource.HERITAGE_ENTRY.name())),
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
        memoryVoteService.attachVoteStats(existing, viewerUserId);
        return existing;
    }

    private static int similarityScore(
            MemoryEntry candidate,
            CreateFamilyMemoryRequest request,
            Map<String, Object> incomingMetadata) {
        Map<String, Object> candidateMetadata = toMutableMap(candidate.getMetadata());
        Map<String, Object> candidateCard = mapValue(candidateMetadata.get("memoryCard"));
        Map<String, Object> incomingCard = request.getMemoryCard() == null ? Map.of() : request.getMemoryCard();
        int score = 0;

        if (sameNonBlank(asText(candidateMetadata.get("scenario")), asText(incomingMetadata.get("scenario")))) score += 4;
        if (sameNonBlank(asText(candidateMetadata.get("target")), asText(incomingMetadata.get("target")))) score += 3;
        if (sameNonBlank(asText(candidateCard.get("theme")), asText(incomingCard.get("theme")))) score += 3;
        if (sameNonBlank(asText(candidateCard.get("title")), asText(incomingCard.get("title")))) score += 2;

        Set<String> leftSignals = memorySignals(candidate.getType(), candidate.getContent(), candidate.getSummary(), candidateMetadata, candidateCard);
        Set<String> rightSignals = memorySignals(request.getType(), request.getContent(), request.getSummary(), incomingMetadata, incomingCard);
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
        card.put("motto", firstNonBlank(asText(incomingCard.get("motto")), asText(existingCard.get("motto")), localMotto(mergedContent)));
        card.put("risk_points", mergeList(existingCard.get("risk_points"), incomingCard.get("risk_points"), 4));
        card.put("action_suggestions", mergeList(existingCard.get("action_suggestions"), incomingCard.get("action_suggestions"), 5));
        card.put("suitable_for", mergeList(existingCard.get("suitable_for"), incomingCard.get("suitable_for"), 4));
        card.put("sensitivity", strongestSensitivity(asText(existingCard.get("sensitivity")), asText(incomingCard.get("sensitivity"))));
        card.put("safety_note", firstNonBlank(asText(incomingCard.get("safety_note")), asText(existingCard.get("safety_note")), "这是一条家族经验整理，不构成专业诊断。"));
        card.put("merged", true);
        return card;
    }

    private static Set<String> memorySignals(String type, String content, String summary, Map<String, Object> metadata, Map<String, Object> card) {
        Set<String> signals = new LinkedHashSet<>();
        addSignal(signals, type);
        addSignal(signals, asText(metadata.get("scenario")));
        addSignal(signals, asText(metadata.get("target")));
        addSignal(signals, asText(card.get("theme")));
        addSignal(signals, asText(card.get("title")));
        String text = normalizeText(content + " " + summary + " " + asText(card.get("summary")) + " " + asText(card.get("motto")));
        for (String keyword : List.of("牙", "视力", "体态", "睡眠", "运动", "屏幕", "健康", "情绪", "沟通", "选择", "志愿", "专业", "工作", "考研", "规矩", "家风", "风险", "教训")) {
            if (text.contains(keyword)) signals.add(keyword);
        }
        for (String token : text.split("[^\\p{IsHan}\\p{Alnum}]+")) {
            if (token.length() >= 2 && token.length() <= 12) signals.add(token);
        }
        return signals;
    }

    private static void addSignal(Set<String> signals, String value) {
        String text = normalizeText(value);
        if (!text.isBlank()) signals.add(text);
    }

    private static int textOverlapScore(String left, String right) {
        String a = normalizeText(left);
        String b = normalizeText(right);
        if (a.isBlank() || b.isBlank()) return 0;
        if (a.contains(b) || b.contains(a)) return 4;
        Set<String> pieces = new LinkedHashSet<>();
        for (int i = 0; i + 2 <= b.length(); i += 2) {
            String piece = b.substring(i, i + 2);
            if (piece.chars().anyMatch(Character::isLetterOrDigit)) pieces.add(piece);
        }
        int hits = 0;
        for (String piece : pieces) {
            if (a.contains(piece)) hits++;
        }
        return Math.min(4, hits / 2);
    }

    private static String mergeText(String existing, String incoming, int maxLength) {
        List<String> parts = new ArrayList<>();
        for (String value : List.of(existing, incoming)) {
            String text = value == null ? "" : value.trim();
            if (!text.isBlank() && parts.stream().noneMatch(text::equals)) parts.add(text);
        }
        String merged = String.join("\n\n补充：", parts);
        if (merged.length() <= maxLength) return merged;
        return merged.substring(0, Math.max(0, maxLength - 1)).strip() + "…";
    }

    static List<String> mergeList(Object left, Object right, int limit) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectList(values, left);
        collectList(values, right);
        return values.stream().limit(limit).toList();
    }

    private static void collectList(Set<String> values, Object value) {
        if (!(value instanceof List<?> list)) return;
        for (Object item : list) {
            String text = asText(item);
            if (!text.isBlank()) values.add(text.length() > 100 ? text.substring(0, 100) : text);
        }
    }

    private static String strongestSensitivity(String left, String right) {
        List<String> order = List.of("LOW", "MEDIUM", "HIGH");
        int ai = order.indexOf(left == null ? "LOW" : left.trim().toUpperCase(Locale.ROOT));
        int bi = order.indexOf(right == null ? "LOW" : right.trim().toUpperCase(Locale.ROOT));
        return order.get(Math.max(Math.max(ai, 0), Math.max(bi, 0)));
    }

    private static boolean sameNonBlank(String left, String right) {
        String a = normalizeText(left);
        String b = normalizeText(right);
        return !a.isBlank() && a.equals(b);
    }

    private static String localMotto(String content) {
        String text = content == null ? "" : content;
        if (text.matches(".*(牙|视力|体态|睡眠|运动|健康).*")) return "小患早察，久安可期";
        if (text.matches(".*(选择|决定|志愿|专业|工作|考研).*")) return "大事慢决，远路慎行";
        if (text.matches(".*(沟通|争吵|理解|亲子|家人).*")) return "言有余地，心有回声";
        return "事经一回，智留一寸";
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    static String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static int intValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return 0; }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toMutableMap(Object metadata) {
        if (metadata instanceof Map<?, ?> map) return new HashMap<>((Map<String, Object>) map);
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) return new HashMap<>((Map<String, Object>) map);
        return new HashMap<>();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
