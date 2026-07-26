package com.familyagent.common.constant;

import java.util.Set;

public enum PersonalMemoryVisibility {
    PRIVATE,
    ALL_FAMILIES_VISIBLE,
    SELECTED_FAMILIES_VISIBLE,
    CARE_VISIBLE;

    public static Set<String> names() {
        return Set.of(
                PRIVATE.name(),
                ALL_FAMILIES_VISIBLE.name(),
                SELECTED_FAMILIES_VISIBLE.name(),
                CARE_VISIBLE.name());
    }
}
