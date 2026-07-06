package com.familyagent.module.agent.harness.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RecallFamilyMemoryOutput(String context, Map<String, Object> metadata) {

    public RecallFamilyMemoryOutput(String context) {
        this(context, Map.of());
    }

    public RecallFamilyMemoryOutput {
        context = context == null ? "" : context;
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
