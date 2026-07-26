package com.familyagent.common.constant;

import java.util.Set;

public enum PersonalMemoryType {
    NOTE,
    KNOWLEDGE,
    INSIGHT,
    EXPERIENCE,
    PREFERENCE,
    PLAN;

    public static final PersonalMemoryType DEFAULT = NOTE;

    public static Set<String> names() {
        return Set.of(
                NOTE.name(),
                KNOWLEDGE.name(),
                INSIGHT.name(),
                EXPERIENCE.name(),
                PREFERENCE.name(),
                PLAN.name());
    }
}
