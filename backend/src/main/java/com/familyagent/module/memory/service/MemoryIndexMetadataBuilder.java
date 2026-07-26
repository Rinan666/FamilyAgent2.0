package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class MemoryIndexMetadataBuilder {

    private static final Map<String, List<String>> TOPIC_KEYWORDS = Map.of(
            "HEALTH", List.of("牙", "视力", "体态", "睡眠", "运动", "健康", "就医", "屏幕", "近视", "刷牙"),
            "EMOTION", List.of("难过", "开心", "焦虑", "压力", "委屈", "生气", "失落", "担心", "烦躁", "释然"),
            "FAMILY_STORY", List.of("爷爷", "奶奶", "外公", "外婆", "长辈", "以前", "小时候", "故事", "当年"),
            "CHOICE", List.of("选择", "决定", "志愿", "升学", "专业", "工作", "考研", "取舍"),
            "COMMUNICATION", List.of("沟通", "争吵", "理解", "说话", "关系", "家人", "亲子"));

    private static final Map<String, List<String>> SCENE_KEYWORDS = Map.of(
            "成长健康", List.of("牙", "视力", "体态", "睡眠", "运动", "屏幕", "健康"),
            "人生选择", List.of("选择", "决定", "志愿", "升学", "专业", "工作", "考研"),
            "情绪复盘", List.of("难过", "焦虑", "委屈", "压力", "失落", "释然", "反思", "复盘"),
            "家族传承", List.of("爷爷", "奶奶", "外公", "外婆", "长辈", "家风", "规矩", "经验", "教训"),
            "家庭沟通", List.of("沟通", "争吵", "理解", "关系", "亲子", "家人"));

    private static final Pattern PERSON_PATTERN = Pattern.compile(
            "(爷爷|奶奶|外公|外婆|爸爸|妈妈|父亲|母亲|孩子|儿子|女儿|孙子|孙女|长辈|家人|我|我们)");

    private MemoryIndexMetadataBuilder() {
    }

    public static Map<String, Object> enrichDiary(
            Map<String, Object> metadata,
            String content,
            String entryType,
            String mood,
            String[] tags) {
        Map<String, Object> next = mutable(metadata);
        Map<String, Object> index = baseIndex("DIARY", content);
        index.put("entryType", safeUpper(entryType, "DAILY"));
        index.put("mood", blankToNull(mood));
        index.put("tags", tags == null ? List.of() : List.of(tags));
        attachRetention(index);
        index.put("temporalLayer", inferTemporalLayer(null, "DIARY", index));
        next.put("index", index);
        return next;
    }

    public static Map<String, Object> enrichFamilyMemory(
            Map<String, Object> metadata,
            String content,
            String summary,
            String memoryType,
            int importance) {
        return enrichMemory(metadata, content, summary, memoryType, importance, "FAMILY_MEMORY");
    }

    public static Map<String, Object> enrichPersonalMemory(
            Map<String, Object> metadata,
            String content,
            String summary,
            String memoryType,
            int importance) {
        return enrichMemory(metadata, content, summary, memoryType, importance, "PERSONAL_MEMORY");
    }

    private static Map<String, Object> enrichMemory(
            Map<String, Object> metadata,
            String content,
            String summary,
            String memoryType,
            int importance,
            String sourceKind) {
        Map<String, Object> next = mutable(metadata);
        String text = join(content, summary, asText(next.get("scenario")));
        Map<String, Object> index = baseIndex(sourceKind, text);
        index.put("memoryType", safeUpper(memoryType, MemoryType.DEFAULT.name()));
        index.put("importance", clamp(importance, 1, 5));
        attachRetention(index);
        index.put("temporalLayer", inferTemporalLayer(null, sourceKind, index));
        next.put("index", index);
        return next;
    }

    public static Map<String, Object> enrichGrowth(
            Map<String, Object> metadata,
            String content,
            String category,
            int severity,
            LocalDate observedAt) {
        Map<String, Object> next = mutable(metadata);
        Map<String, Object> index = baseIndex("GROWTH_OBSERVATION", content);
        index.put("category", safeUpper(category, "OTHER"));
        index.put("severity", clamp(severity, 1, 5));
        index.put("eventDate", observedAt == null ? null : observedAt.toString());
        attachRetention(index);
        index.put("temporalLayer", inferTemporalLayer(observedAt, "GROWTH_OBSERVATION", index));
        next.put("index", index);
        return next;
    }

    public static int indexBoost(Object metadata, String query) {
        if (!(metadata instanceof Map<?, ?> map) || !(map.get("index") instanceof Map<?, ?> index)) {
            return 0;
        }
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return 0;
        }
        int boost = 0;
        boost += labelKeywordOverlap(index.get("topics"), normalizedQuery, TOPIC_KEYWORDS) * 8;
        boost += labelKeywordOverlap(index.get("scenes"), normalizedQuery, SCENE_KEYWORDS) * 6;
        boost += listOverlap(index.get("people"), normalizedQuery) * 5;
        Object rawLayer = index.get("temporalLayer");
        String layer = rawLayer == null ? "" : String.valueOf(rawLayer);
        if ("CORE".equals(layer) || "STABLE".equals(layer)) {
            boost += 3;
        }
        return boost;
    }

    public static double relevanceWeight(Object metadata, LocalDateTime fallbackTime) {
        if (!(metadata instanceof Map<?, ?> map)) {
            return 1.0;
        }
        if (!(map.get("index") instanceof Map<?, ?> index)) {
            return clamp(socialWeight(map), 0.2, 4.0);
        }
        if (Boolean.TRUE.equals(map.get("coreMemory")) || "true".equalsIgnoreCase(asText(map.get("coreMemory")))) {
            return clamp(1.08 * socialWeight(map), 0.2, 4.32);
        }
        Retention retention = retentionFromIndex(index);
        LocalDateTime referenceTime = referenceTime(index, fallbackTime);
        if (referenceTime == null) {
            return clamp(retention.minWeight() * socialWeight(map), retention.minWeight() * 0.2, 4.32);
        }
        long days = Math.max(0, Duration.between(referenceTime, LocalDateTime.now()).toDays());
        double decayed = retention.minWeight()
                + (1.0 - retention.minWeight()) * Math.exp(-Math.log(2.0) * days / retention.halfLifeDays());
        return clamp(decayed * socialWeight(map), retention.minWeight() * 0.2, 4.32);
    }

    private static double socialWeight(Map<?, ?> metadata) {
        double weight = 1.0;
        if (metadata.get("voteStats") instanceof Map<?, ?> stats) {
            weight *= clamp(doubleValue(stats.get("consensusWeight"), 1.0), 0.6, 4.0);
        }
        if (metadata.get("stalenessStats") instanceof Map<?, ?> stats) {
            weight *= clamp(doubleValue(stats.get("stalenessWeight"), 1.0), 0.2, 1.0);
        }
        return weight;
    }

    private static Map<String, Object> baseIndex(String sourceKind, String text) {
        Map<String, Object> index = new HashMap<>();
        index.put("version", 1);
        index.put("sourceKind", sourceKind);
        index.put("people", extractPeople(text));
        index.put("topics", inferLabels(text, TOPIC_KEYWORDS));
        index.put("scenes", inferLabels(text, SCENE_KEYWORDS));
        index.put("emotion", inferEmotion(text));
        index.put("indexedAt", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        return index;
    }

    private static void attachRetention(Map<String, Object> index) {
        Retention retention = retentionProfile(index);
        index.put("retentionProfile", retention.code());
        index.put("retentionLabel", retention.label());
        index.put("halfLifeDays", retention.halfLifeDays());
        index.put("minTemporalWeight", retention.minWeight());
        index.put("retentionNote", retention.note());
    }

    private static Retention retentionProfile(Map<?, ?> index) {
        String sourceKind = asText(index.get("sourceKind"));
        if ("GROWTH_OBSERVATION".equals(sourceKind)) {
            String category = asText(index.get("category"));
            int severity = intValue(index.get("severity"), 3);
            Retention base = switch (category) {
                case "EMOTION", "COMMUNICATION" -> new Retention(
                        "SHORT_SIGNAL",
                        "短期信号",
                        21,
                        0.18,
                        "情绪和沟通信号变化很快，过期后只能提示曾经出现过。");
                case "DENTAL", "VISION", "POSTURE", "SLEEP", "SCREEN_TIME", "EXERCISE" -> new Retention(
                        "RECHECK_SIGNAL",
                        "复核信号",
                        60,
                        0.28,
                        "健康和习惯观察需要按复核周期衰退，不能长期当作当前状态。");
                default -> new Retention(
                        "GENERAL_SIGNAL",
                        "一般观察",
                        45,
                        0.22,
                        "普通观察会随时间淡化，需要结合新的记录判断。");
            };
            if (severity >= 4) {
                return new Retention(
                        base.code(),
                        base.label(),
                        Math.round(base.halfLifeDays() * 1.4),
                        Math.min(0.4, base.minWeight() + 0.06),
                        base.note() + "严重度较高时保留更久，但仍需复核。");
            }
            return base;
        }

        if ("FAMILY_MEMORY".equals(sourceKind)) {
            String memoryType = asText(index.get("memoryType"));
            int importance = intValue(index.get("importance"), 3);
            if (importance >= 5) {
                return new Retention(
                        "CORE_LEGACY",
                        "核心沉淀",
                        3650,
                        0.86,
                        "核心记忆和价值观类内容基本不按短期事实衰退。");
            }
            return switch (memoryType) {
                case "VALUE", "ELDER_ADVICE", "FAMILY_STORY" -> new Retention(
                        "LEGACY",
                        "长期传承",
                        1460,
                        0.68,
                        "家族故事、价值观和长者经验衰退较慢，主要作为判断背景。");
                case "HEALTH_REMINDER", "GROWTH_RISK" -> new Retention(
                        "PRACTICAL_REMINDER",
                        "实践提醒",
                        365,
                        0.46,
                        "健康提醒和风险经验会随环境变化衰退，需要结合当下情况。");
                default -> new Retention(
                        "STABLE_MEMORY",
                        "稳定经验",
                        730,
                        0.56,
                        "经验沉淀比日记稳定，但不应机械套用。");
            };
        }

        String entryType = asText(index.get("entryType"));
        List<String> topics = stringList(index.get("topics"));
        List<String> tags = stringList(index.get("tags"));
        if ("EMOTION".equals(entryType) || topics.contains("EMOTION") || tags.contains("短期情绪")) {
            return new Retention(
                    "EPHEMERAL_EMOTION",
                    "短期情绪",
                    14,
                    0.14,
                    "情绪日记非常容易过时，只能说明当时感受。");
        }
        if ("MESSAGE_TO_FAMILY".equals(entryType) || tags.contains("长期留存")) {
            return new Retention(
                    "KEPT_MESSAGE",
                    "长期留存",
                    365,
                    0.42,
                    "给家人的话比普通日记稳定，但仍是某个时间点的表达。");
        }
        if ("LESSON".equals(entryType) || "SELF_REFLECTION".equals(entryType) || tags.contains("阶段复盘")) {
            return new Retention(
                    "REFLECTION",
                    "阶段复盘",
                    180,
                    0.34,
                    "复盘类日记保留较久，但最好沉淀为经验后再长期使用。");
        }
        if (topics.contains("HEALTH") || tags.contains("短期线索")) {
            return new Retention(
                    "LIFE_SIGNAL",
                    "短期线索",
                    45,
                    0.2,
                    "生活线索会较快变化，需要新记录复核。");
        }
        return new Retention(
                "DAILY_SNAPSHOT",
                "短期片段",
                30,
                0.18,
                "普通日记容易过时，主要作为近期上下文。");
    }

    private static Retention retentionFromIndex(Map<?, ?> index) {
        String code = asText(index.get("retentionProfile"));
        String label = asText(index.get("retentionLabel"));
        String note = asText(index.get("retentionNote"));
        long halfLife = longValue(index.get("halfLifeDays"), -1);
        double minWeight = doubleValue(index.get("minTemporalWeight"), -1);
        if (halfLife > 0 && minWeight >= 0) {
            return new Retention(
                    code.isBlank() ? "CUSTOM" : code,
                    label.isBlank() ? "自定义时效" : label,
                    halfLife,
                    clamp(minWeight, 0.05, 0.95),
                    note);
        }
        return retentionProfile(index);
    }

    private static LocalDateTime referenceTime(Map<?, ?> index, LocalDateTime fallbackTime) {
        for (String key : List.of("eventDate", "observedAt", "occurredAt", "recordedAt", "indexedAt")) {
            LocalDateTime parsed = parseTime(asText(index.get(key)));
            if (parsed != null) {
                return parsed;
            }
        }
        return fallbackTime;
    }

    private static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            // Try date-only below.
        }
        try {
            return LocalDate.parse(value.trim()).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String inferTemporalLayer(LocalDate eventDate, String sourceKind, Map<String, Object> index) {
        if ("FAMILY_MEMORY".equals(sourceKind)) {
            return "STABLE";
        }
        if (eventDate != null && eventDate.isBefore(LocalDate.now().minusDays(180))) {
            return "FADING";
        }
        Object topics = index.get("topics");
        if (topics instanceof List<?> list && list.contains("EMOTION")) {
            return "RECENT_SIGNAL";
        }
        return "RECENT";
    }

    private static List<String> extractPeople(String text) {
        Set<String> people = new LinkedHashSet<>();
        var matcher = PERSON_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            people.add(matcher.group(1));
        }
        return new ArrayList<>(people);
    }

    private static List<String> inferLabels(String text, Map<String, List<String>> dictionary) {
        String normalized = normalize(text);
        List<String> labels = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : dictionary.entrySet()) {
            if (entry.getValue().stream().anyMatch(normalized::contains)) {
                labels.add(entry.getKey());
            }
        }
        return labels;
    }

    private static String inferEmotion(String text) {
        String normalized = normalize(text);
        if (List.of("难过", "焦虑", "压力", "委屈", "生气", "失落", "烦躁").stream().anyMatch(normalized::contains)) {
            return "NEGATIVE";
        }
        if (List.of("开心", "感动", "释然", "希望").stream().anyMatch(normalized::contains)) {
            return "POSITIVE";
        }
        return "NEUTRAL";
    }

    private static int listOverlap(Object value, String normalizedQuery) {
        if (!(value instanceof List<?> list)) {
            return 0;
        }
        int count = 0;
        for (Object item : list) {
            if (item != null && normalizedQuery.contains(String.valueOf(item).toLowerCase(Locale.ROOT))) {
                count += 1;
            }
        }
        return count;
    }

    private static int labelKeywordOverlap(Object value, String normalizedQuery, Map<String, List<String>> dictionary) {
        if (!(value instanceof List<?> list)) {
            return 0;
        }
        int count = 0;
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String label = String.valueOf(item);
            if (normalizedQuery.contains(label.toLowerCase(Locale.ROOT))) {
                count += 1;
                continue;
            }
            List<String> keywords = dictionary.getOrDefault(label, List.of());
            if (keywords.stream().anyMatch(keyword -> normalizedQuery.contains(keyword.toLowerCase(Locale.ROOT)))) {
                count += 1;
            }
        }
        return count;
    }

    private static Map<String, Object> mutable(Map<String, Object> metadata) {
        return metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String safeUpper(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String join(String... values) {
        return String.join(" ", java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList());
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(String::valueOf)
                .toList();
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Retention(String code, String label, long halfLifeDays, double minWeight, String note) {}
}
