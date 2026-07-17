package com.familyagent.infra.ai;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class AIClientRequestSupport {

    static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";
    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String RUN_ID_HEADER = "X-Agent-Run-Id";
    static final String ERROR_NONE = "NONE";
    static final String ERROR_AI_SERVICE = "AI_SERVICE_ERROR";

    private final String baseUrl;
    private final String internalToken;
    private final MeterRegistry meterRegistry;

    public AIClientRequestSupport(
            @Value("${ai-service.base-url:http://localhost:8000}") String baseUrl,
            @Value("${ai-service.internal-token:}") String internalToken,
            MeterRegistry meterRegistry) {
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.meterRegistry = meterRegistry;
    }

    public String url(String path) {
        return baseUrl + path;
    }

    public String newRequestId() {
        return "ai-" + UUID.randomUUID();
    }

    public String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return newRequestId();
        }
        String trimmed = requestId.trim();
        return trimmed.length() <= 128 ? trimmed : trimmed.substring(0, 128);
    }

    public void addInternalHeaders(HttpHeaders headers, String requestId, Long runId) {
        headers.set(REQUEST_ID_HEADER, normalizeRequestId(requestId));
        if (runId != null) {
            headers.set(RUN_ID_HEADER, String.valueOf(runId));
        }
        if (internalToken != null && !internalToken.isBlank()) {
            headers.set(INTERNAL_SERVICE_TOKEN_HEADER, internalToken);
        }
    }

    public Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    public String metricErrorCode(boolean success, String errorCode) {
        if (success) {
            return ERROR_NONE;
        }
        return errorCode == null || errorCode.isBlank() ? "AI_BUSINESS_FAILURE" : errorCode;
    }

    public void record(
            String operation,
            boolean success,
            String errorCode,
            String provider,
            String model,
            boolean degraded,
            Duration duration) {
        meterRegistry.timer(
                "familyagent.ai.client.request",
                "operation", operation,
                "success", Boolean.toString(success),
                "errorCode", tagValue(errorCode),
                "provider", tagValue(provider),
                "model", tagValue(model),
                "degraded", Boolean.toString(degraded))
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static String tagValue(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.length() <= 80 ? value : value.substring(0, 80);
    }
}
