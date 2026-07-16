package com.familyagent.module.memory.dto;

public record EmbeddingCallObservation(
        boolean attempted,
        boolean success,
        boolean degraded,
        String provider,
        String model,
        Integer dimensions,
        Long latencyMs,
        String errorCode) {

    public EmbeddingCallObservation {
        provider = normalize(provider);
        model = normalize(model);
        dimensions = dimensions == null ? null : Math.max(0, dimensions);
        latencyMs = latencyMs == null ? null : Math.max(0, latencyMs);
        errorCode = normalize(errorCode);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
