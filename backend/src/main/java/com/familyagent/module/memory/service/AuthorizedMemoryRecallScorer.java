package com.familyagent.module.memory.service;

import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class AuthorizedMemoryRecallScorer {

    public boolean supports(AuthorizedMemoryRecallCandidate candidate, String query) {
        MemoryEntry entry = candidate.entry();
        return hasSupport(searchText(entry), entry.getMetadata(), query);
    }

    public double score(AuthorizedMemoryRecallCandidate candidate, String query) {
        MemoryEntry entry = candidate.entry();
        int baseScore = textScore(searchText(entry), query)
                + MemoryIndexMetadataBuilder.indexBoost(entry.getMetadata(), query);
        return baseScore * MemoryIndexMetadataBuilder.relevanceWeight(
                entry.getMetadata(),
                entry.getOccurredAt() == null
                        ? (entry.getUpdatedAt() == null ? entry.getCreatedAt() : entry.getUpdatedAt())
                        : entry.getOccurredAt());
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

    private static String searchText(MemoryEntry entry) {
        return safe(entry.getTitle())
                + " " + safe(entry.getContent())
                + " " + safe(entry.getSummary())
                + " " + safe(entry.getType())
                + " " + String.join(" ", entry.getTags() == null ? new String[0] : entry.getTags())
                + " " + mapText(entry.getMetadata());
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
        return safe(value);
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalize(String value) {
        return safe(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{P}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
