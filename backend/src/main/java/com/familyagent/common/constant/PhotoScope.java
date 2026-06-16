package com.familyagent.common.constant;

import java.util.Locale;

public enum PhotoScope {
    PERSONAL,
    FAMILY;

    public static PhotoScope fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return FAMILY;
        }
        try {
            return PhotoScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FAMILY;
        }
    }
}
