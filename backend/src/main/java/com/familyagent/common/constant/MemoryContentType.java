package com.familyagent.common.constant;

import java.util.Locale;
import java.util.Set;

public enum MemoryContentType {
    NOTE,
    KNOWLEDGE,
    INSIGHT,
    EXPERIENCE,
    OBSERVATION,
    PREFERENCE,
    PLAN;

    public static final MemoryContentType DEFAULT = NOTE;

    public static Set<String> names() {
        return Set.of(
                NOTE.name(),
                KNOWLEDGE.name(),
                INSIGHT.name(),
                EXPERIENCE.name(),
                OBSERVATION.name(),
                PREFERENCE.name(),
                PLAN.name());
    }

    public static MemoryContentType fromDiaryEntryType(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "LESSON" -> KNOWLEDGE;
            case "EMOTION", "SELF_REFLECTION" -> INSIGHT;
            default -> NOTE;
        };
    }

    public static MemoryContentType fromFamilyMemoryType(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "NOTE" -> NOTE;
            case "LEARNING", "KNOWLEDGE", "ELDER_ADVICE" -> KNOWLEDGE;
            case "MISTAKE", "INSIGHT", "VALUE" -> INSIGHT;
            case "EXPERIENCE", "FAMILY_STORY" -> EXPERIENCE;
            case "OBSERVATION", "GROWTH_RISK" -> OBSERVATION;
            case "PREFERENCE" -> PREFERENCE;
            case "PLAN", "HEALTH_REMINDER" -> PLAN;
            default -> null;
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
