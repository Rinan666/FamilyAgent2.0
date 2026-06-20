package com.familyagent.common.constant;

import java.util.Locale;

public enum MediaRecordType {
    DIARY,
    GROWTH,
    MEMORY;

    public static MediaRecordType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Media record type is required");
        }
        return MediaRecordType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
