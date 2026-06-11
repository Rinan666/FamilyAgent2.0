package com.familyagent.common.constant;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum HeritageSource {
    HERITAGE_ENTRY,
    HERITAGE_INTERVIEW,
    HERITAGE_ATOM;

    public static Set<String> names() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
