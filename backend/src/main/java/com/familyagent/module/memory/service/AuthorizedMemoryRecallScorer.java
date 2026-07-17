package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class AuthorizedMemoryRecallScorer {

    public boolean supports(DiaryEntry entry, String query) {
        return hasSupport(diarySearchText(entry), entry.getMetadata(), query);
    }

    public boolean supports(MemoryEntry entry, String query) {
        return hasSupport(memorySearchText(entry), entry.getMetadata(), query);
    }

    public boolean supports(GrowthGuardRecord record, String query) {
        return hasSupport(growthSearchText(record), record.getMetadata(), query);
    }

    public double score(DiaryEntry entry, String query) {
        int baseScore = textScore(diarySearchText(entry), query)
                + MemoryIndexMetadataBuilder.indexBoost(entry.getMetadata(), query);
        return baseScore * MemoryIndexMetadataBuilder.relevanceWeight(entry.getMetadata(), entry.getCreatedAt());
    }

    public double score(MemoryEntry entry, String query) {
        int baseScore = textScore(memorySearchText(entry), query)
                + MemoryIndexMetadataBuilder.indexBoost(entry.getMetadata(), query);
        return baseScore * MemoryIndexMetadataBuilder.relevanceWeight(
                entry.getMetadata(),
                entry.getUpdatedAt() == null ? entry.getCreatedAt() : entry.getUpdatedAt());
    }

    public double score(GrowthGuardRecord record, String query) {
        int baseScore = textScore(growthSearchText(record), query)
                + MemoryIndexMetadataBuilder.indexBoost(record.getMetadata(), query);
        return baseScore * MemoryIndexMetadataBuilder.relevanceWeight(
                record.getMetadata(),
                record.getObservedAt() == null ? record.getCreatedAt() : record.getObservedAt().atStartOfDay());
    }

    private static boolean hasSupport(String text, Object metadata, String query) {
        return query != null
                && !query.isBlank()
                && (textScore(text, query) > 0 || MemoryIndexMetadataBuilder.indexBoost(metadata, query) > 0);
    }

    private static int textScore(String text, String query) {
        if (query.isBlank()) {
            return 0;
        }
        String target = normalize(text);
        int score = target.contains(query) ? 20 : 0;
        for (String token : query.split("\\s+")) {
            if (token.length() >= 2 && target.contains(token)) {
                score += Math.min(10, token.length());
            }
        }
        for (int index = 0; index < query.length(); index += 2) {
            String piece = query.substring(index, Math.min(index + 2, query.length()));
            if (piece.length() == 2 && target.contains(piece)) {
                score += 1;
            }
        }
        return score;
    }

    private static String diarySearchText(DiaryEntry entry) {
        return entry.getRawText()
                + " " + mapText(entry.getStructured())
                + " " + mapText(entry.getMetadata())
                + " " + String.join(" ", entry.getTags() == null ? new String[0] : entry.getTags());
    }

    private static String memorySearchText(MemoryEntry entry) {
        return entry.getContent() + " " + entry.getSummary() + " " + mapText(entry.getMetadata());
    }

    private static String growthSearchText(GrowthGuardRecord record) {
        return record.getContent()
                + " " + record.getCategory()
                + " " + record.getSeverity()
                + " " + record.getObservedAt()
                + " " + mapText(record.getMetadata());
    }

    private static String mapText(Object value) {
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder();
            for (Object item : map.values()) {
                if (item != null) {
                    builder.append(item).append(' ');
                }
            }
            return builder.toString();
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}，。！？；：“”‘’（）【】《》]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
