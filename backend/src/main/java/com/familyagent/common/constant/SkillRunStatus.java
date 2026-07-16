package com.familyagent.common.constant;

import java.util.Locale;

public enum SkillRunStatus {
    PLANNED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED;

    public static SkillRunStatus parse(String value, SkillRunStatus fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
