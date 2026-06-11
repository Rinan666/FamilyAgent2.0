package com.familyagent.common.constant;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum MemoryType {
    FAMILY_STORY,
    ELDER_ADVICE,
    HEALTH_REMINDER,
    GROWTH_RISK,
    VALUE,
    PLAN;

    public static final MemoryType DEFAULT = ELDER_ADVICE;

    public static Set<String> names() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
