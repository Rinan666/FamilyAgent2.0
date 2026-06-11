package com.familyagent.common.constant;

import java.util.Set;

public enum MemoryScope {
    PRIVATE,
    PARENT_VISIBLE,
    CARE_VISIBLE,
    FAMILY_VISIBLE,
    /** diary-only visibility level */
    LEGACY_VISIBLE;

    public static final MemoryScope DEFAULT_MEMORY = FAMILY_VISIBLE;
    public static final MemoryScope DEFAULT_GROWTH = CARE_VISIBLE;
    public static final MemoryScope DEFAULT_DIARY = PRIVATE;
    public static final MemoryScope DEFAULT_SESSION = PRIVATE;

    /** Returns the names of all scopes valid for family memories (excludes LEGACY_VISIBLE). */
    public static Set<String> familyNames() {
        return Set.of(PRIVATE.name(), PARENT_VISIBLE.name(), CARE_VISIBLE.name(), FAMILY_VISIBLE.name());
    }

    /** Returns the names of all scopes valid for diary entries (includes LEGACY_VISIBLE). */
    public static Set<String> diaryNames() {
        return Set.of(PRIVATE.name(), PARENT_VISIBLE.name(), CARE_VISIBLE.name(), FAMILY_VISIBLE.name(), LEGACY_VISIBLE.name());
    }
}
