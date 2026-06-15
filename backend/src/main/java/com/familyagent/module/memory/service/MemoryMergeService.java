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

        String mergedContent = mergeText(existing.getContent(), request.getContent(), 1200);
        String mergedSummary = firstNonBlank(
                request.getSummary(),
                existing.getSummary(),
                mergedContent);

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
        int score = 0;

        if (sameNonBlank(asText(candidateMetadata.get("scenario")), asText(incomingMetadata.get("scenario")))) score += 4;
        if (sameNonBlank(asText(candidateMetadata.get("target")), asText(incomingMetadata.get("target")))) score += 3;

        Set<String> leftSignals = memorySignals(candidate.getType(), candidate.getContent(), candidate.getSummary(), candidateMetadata);
        Set<String> rightSignals = memorySignals(request.getType(), request.getContent(), request.getSummary(), incomingMetadata);
        leftSignals.retainAll(rightSignals);
        score += Math.min(6, leftSignals.size());
        score += textOverlapScore(candidate.getContent() + " " + candidate.getSummary(), request.getContent());
        return score;
    }

    private static Set<String> memorySignals(String type, String content, String summary, Map<String, Object> metadata) {
        Set<String> signals = new LinkedHashSet<>();
        addSignal(signals, type);
        addSignal(signals, asText(metadata.get("scenario")));
        addSignal(signals, asText(metadata.get("target")));
        String text = normalizeText(content + " " + summary);
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

    private static boolean sameNonBlank(String left, String right) {
        String a = normalizeText(left);
        String b = normalizeText(right);
        return !a.isBlank() && a.equals(b);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
