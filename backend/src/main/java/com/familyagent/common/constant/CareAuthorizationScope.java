package com.familyagent.common.constant;

import java.util.Set;

public enum CareAuthorizationScope {
    ALL,
    DIARY,
    MEMORY,
    GROWTH_GUARD;

    public static Set<String> names() {
        return Set.of(ALL.name(), DIARY.name(), MEMORY.name(), GROWTH_GUARD.name());
    }
}
