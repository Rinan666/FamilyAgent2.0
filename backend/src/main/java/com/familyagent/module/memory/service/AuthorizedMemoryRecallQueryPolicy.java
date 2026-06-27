package com.familyagent.module.memory.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class AuthorizedMemoryRecallQueryPolicy {

    private static final String FAMILY_AGENT_SCENE = "FAMILY_AGENT";

    private static final List<String> FAMILY_RELEVANCE_TERMS = List.of(
            "family", "diary", "memory", "growth", "parent", "child", "study",
            "tooth", "teeth", "dental", "screen", "sleep", "health", "exercise", "emotion",
            "choice", "decision", "school", "career", "communication", "relationship",
            "\u5bb6", "\u5bb6\u5ead", "\u5bb6\u4eba", "\u7236\u6bcd", "\u7238\u7238", "\u5988\u5988",
            "\u7237\u7237", "\u5976\u5976", "\u5916\u516c", "\u5916\u5a46", "\u5b69\u5b50", "\u513f\u5b50",
            "\u5973\u513f", "\u4eb2\u5b50", "\u6210\u957f", "\u5b66\u4e60", "\u5b66\u6821", "\u4f5c\u4e1a",
            "\u8003\u8bd5", "\u5fd7\u613f", "\u9009\u62e9", "\u6c9f\u901a", "\u5173\u7cfb", "\u65e5\u8bb0",
            "\u8bb0\u5f55", "\u8bb0\u5fc6", "\u7ecf\u9a8c", "\u6c89\u6dc0", "\u4f20\u627f", "\u6545\u4e8b",
            "\u89c2\u5bdf", "\u60c5\u7eea", "\u7126\u8651", "\u538b\u529b", "\u5065\u5eb7", "\u7259",
            "\u5237\u7259", "\u89c6\u529b", "\u7761\u7720", "\u8fd0\u52a8", "\u4f53\u6001", "\u624b\u673a",
            "\u5c4f\u5e55", "\u4e60\u60ef", "\u966a\u4f34", "\u6559\u80b2", "\u4fdd\u5b58",
            "\u8bb0\u5f55\u4e0b\u6765", "\u60f3\u8d77\u6765");

    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public boolean shouldRecallFamilyContext(String query, String scene) {
        if (FAMILY_AGENT_SCENE.equalsIgnoreCase(scene == null ? "" : scene.trim())) {
            return true;
        }
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return true;
        }
        String compactQuery = normalizedQuery.replace(" ", "");
        return FAMILY_RELEVANCE_TERMS.stream().anyMatch(term ->
                normalizedQuery.contains(term) || compactQuery.contains(term));
    }
}
