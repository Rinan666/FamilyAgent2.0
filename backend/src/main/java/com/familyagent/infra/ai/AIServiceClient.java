package com.familyagent.infra.ai;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.dto.AgentChatStreamPayload;
import com.familyagent.infra.ai.dto.EmbeddingRequest;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.infra.ai.dto.SaveMemoryPlanPayload;
import com.familyagent.infra.ai.dto.SaveMemoryPlanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * AI service client for the Python FastAPI service.
 * <p>
 * All external dependencies ({@link RestTemplate}, {@link ObjectMapper}) are
 * managed by Spring and injected — no manual construction.
 */
@Slf4j
@Component
public class AIServiceClient {

    private static final int STREAM_BUFFER_SIZE = 1024;
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String RUN_ID_HEADER = "X-Agent-Run-Id";
    private static final String ERROR_NONE = "NONE";
    private static final String ERROR_AI_SERVICE = "AI_SERVICE_ERROR";

    private final RestTemplate restTemplate;
    private final RestTemplate streamRestTemplate;
    private final String baseUrl;
    private final String internalToken;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final SaveMemoryPlanClient saveMemoryPlanClient;

    public AIServiceClient(@Qualifier("aiServiceRestTemplate") RestTemplate restTemplate,
                           @Qualifier("aiServiceStreamRestTemplate") RestTemplate streamRestTemplate,
                           @Value("${ai-service.base-url:http://localhost:8000}") String baseUrl,
                           @Value("${ai-service.internal-token:}") String internalToken,
                           ObjectMapper objectMapper,
                           MeterRegistry meterRegistry,
                           SaveMemoryPlanClient saveMemoryPlanClient) {
        this.restTemplate = restTemplate;
        this.streamRestTemplate = streamRestTemplate;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.saveMemoryPlanClient = saveMemoryPlanClient;
    }

    public SaveMemoryPlanResponse planSaveMemory(SaveMemoryPlanPayload payload, String requestId) {
        try {
            return saveMemoryPlanClient.plan(payload, normalizeRequestId(requestId));
        } catch (AIServiceInputRejectedException error) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, error.getMessage());
        }
    }

    public SaveMemoryPlanResponse planSaveMemory(
            SaveMemoryPlanPayload payload,
            String requestId,
            Long runId) {
        try {
            return saveMemoryPlanClient.plan(payload, normalizeRequestId(requestId), runId);
        } catch (AIServiceInputRejectedException error) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, error.getMessage());
        }
    }

    /**
     * Proxy FamilyAgent chat SSE streams from the AI service.
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackProxyChatStream")
    public void proxyChatStream(AgentChatStreamPayload request, OutputStream downstream, String authorization, String requestId) {
        proxyChatStreamInternal(request, downstream, authorization, requestId, null);
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackProxyChatStreamWithRun")
    public void proxyChatStream(
            AgentChatStreamPayload request,
            OutputStream downstream,
            String authorization,
            String requestId,
            Long runId) {
        proxyChatStreamInternal(request, downstream, authorization, requestId, runId);
    }

    private void proxyChatStreamInternal(
            AgentChatStreamPayload request,
            OutputStream downstream,
            String authorization,
            String requestId,
            Long runId) {
        long startedAt = System.nanoTime();
        String effectiveRequestId = normalizeRequestId(requestId);
        boolean success = false;
        String errorCode = ERROR_NONE;
        try {
            String jsonBody = objectMapper.writeValueAsString(request);
            URI uri = URI.create(baseUrl + "/ai/agent/chat/stream");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.TEXT_EVENT_STREAM));
            headers.set(REQUEST_ID_HEADER, effectiveRequestId);
            if (runId != null) {
                headers.set(RUN_ID_HEADER, String.valueOf(runId));
            }
            if (internalToken != null && !internalToken.isBlank()) {
                headers.set(INTERNAL_SERVICE_TOKEN_HEADER, internalToken);
            }
            if (authorization != null && !authorization.isBlank()) {
                headers.set("Authorization", authorization);
            }
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            streamRestTemplate.execute(uri, HttpMethod.POST, httpRequest -> writeStreamRequest(httpRequest, entity, jsonBody), response -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    log.warn("AI service stream response error: requestId={}, status={}",
                            effectiveRequestId, response.getStatusCode().value());
                    throw new BusinessException(
                            ErrorCode.AI_SERVICE_ERROR,
                            "AI service unavailable, please retry later."
                    );
                }
                copyStream(response.getBody(), downstream);
                return null;
            });
            success = true;
        } catch (BusinessException e) {
            errorCode = ERROR_AI_SERVICE;
            throw e;
        } catch (HttpStatusCodeException e) {
            errorCode = ERROR_AI_SERVICE;
            log.warn("AI service stream response error: requestId={}, status={}",
                    effectiveRequestId, e.getStatusCode().value());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service unavailable, please retry later.");
        } catch (Exception e) {
            errorCode = "AI_STREAM_INTERRUPTED";
            log.error("FamilyAgent chat SSE proxy failed: requestId={}, errorType={}",
                    effectiveRequestId, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service unavailable, please retry later.");
        } finally {
            recordAiCall("chat_stream", success, errorCode, null, null, false, elapsed(startedAt));
        }
    }

    private void writeStreamRequest(ClientHttpRequest httpRequest, HttpEntity<String> entity, String jsonBody) throws IOException {
        httpRequest.getHeaders().putAll(entity.getHeaders());
        httpRequest.getBody().write(jsonBody.getBytes(StandardCharsets.UTF_8));
        httpRequest.getBody().flush();
    }

    private void copyStream(InputStream upstream, OutputStream downstream) throws IOException {
        byte[] buffer = new byte[STREAM_BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = upstream.read(buffer)) != -1) {
            downstream.write(buffer, 0, bytesRead);
            downstream.flush();
        }
    }

    private String newRequestId() {
        return "ai-" + UUID.randomUUID();
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return newRequestId();
        }
        String trimmed = requestId.trim();
        return trimmed.length() <= 128 ? trimmed : trimmed.substring(0, 128);
    }

    private String streamErrorEvent(String requestId) {
        return streamErrorEvent(requestId, null);
    }

    private String streamErrorEvent(String requestId, Long runId) {
        String safeRequestId = normalizeRequestId(requestId);
        String runField = runId == null ? "" : ",\"runId\":" + runId;
        return "data: {\"type\":\"error\",\"error\":true,\"code\":\"AI_STREAM_UNAVAILABLE\","
                + "\"message\":\"AI service unavailable, please retry later.\",\"retryable\":true,"
                + "\"degraded\":false,\"requestId\":\"" + escapeJson(safeRequestId) + "\""
                + runField + "}\n\n";
    }

    private String escapeJson(String value) {
        String text = value == null ? "" : value;
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private void recordAiCall(String operation, boolean success, Duration duration) {
        recordAiCall(operation, success, ERROR_NONE, null, null, false, duration);
    }

    private void recordAiCall(
            String operation,
            boolean success,
            String errorCode,
            String provider,
            String model,
            boolean degraded,
            Duration duration
    ) {
        meterRegistry.timer(
                "familyagent.ai.client.request",
                "operation", operation,
                "success", Boolean.toString(success),
                "errorCode", tagValue(errorCode),
                "provider", tagValue(provider),
                "model", tagValue(model),
                "degraded", Boolean.toString(degraded)
        ).record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    private String tagValue(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private String metricErrorCode(boolean success, String errorCode) {
        if (success) {
            return ERROR_NONE;
        }
        if (errorCode == null || errorCode.isBlank()) {
            return "AI_BUSINESS_FAILURE";
        }
        return errorCode;
    }

    /**
     * Generate an embedding vector for backend-owned memory indexing.
     */
    @CircuitBreaker(name = "aiService")
    @Retry(name = "aiService", fallbackMethod = "fallbackEmbedText")
    public EmbeddingResponse embedText(EmbeddingRequest request) {
        long startedAt = System.nanoTime();
        String requestId = newRequestId();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Request-Id", requestId);
            if (internalToken != null && !internalToken.isBlank()) {
                headers.set(INTERNAL_SERVICE_TOKEN_HEADER, internalToken);
            }
            HttpEntity<EmbeddingRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<EmbeddingResponse> response = restTemplate.postForEntity(
                    baseUrl + "/ai/embedding/embed", entity, EmbeddingResponse.class);
            EmbeddingResponse body = response.getBody();
            boolean bodySuccess = body != null && body.isSuccess();
            recordAiCall(
                    "embedding",
                    bodySuccess,
                    metricErrorCode(bodySuccess, body != null ? body.getErrorCode() : "AI_EMPTY_RESPONSE"),
                    body != null ? body.getProvider() : null,
                    body != null ? body.getModel() : null,
                    body != null && body.isDegraded(),
                    elapsed(startedAt)
            );
            log.info(
                    "AI embedding completed: requestId={}, provider={}, model={}, dimensions={}, latencyMs={}, degraded={}, errorCode={}",
                    requestId,
                    body != null ? body.getProvider() : null,
                    body != null ? body.getModel() : null,
                    body != null ? body.getDimensions() : null,
                    body != null ? body.getLatencyMs() : null,
                    body != null && body.isDegraded(),
                    body != null ? body.getErrorCode() : null);
            return body;
        } catch (Exception e) {
            recordAiCall(
                    "embedding",
                    false,
                    ERROR_AI_SERVICE,
                    null,
                    null,
                    false,
                    elapsed(startedAt)
            );
            log.warn("Embedding service transport failed: requestId={}, errorType={}",
                    requestId, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service unavailable");
        }
    }

    /**
     * Health check.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> healthCheck() {
        try {
            return restTemplate.getForObject(baseUrl + "/ai/health", Map.class);
        } catch (Exception e) {
            log.error("AI service health check failed: errorType={}", e.getClass().getSimpleName());
            return Map.of("status", "DOWN", "errorCode", ERROR_AI_SERVICE);
        }
    }

    // --- Fallback methods ---

    private EmbeddingResponse fallbackEmbedText(EmbeddingRequest request, Exception ex) {
        log.warn("AI embedding fallback triggered: errorType={}", ex.getClass().getSimpleName());
        return EmbeddingResponse.unavailable();
    }

    private void fallbackProxyChatStream(
            AgentChatStreamPayload request,
            OutputStream downstream,
            String authorization,
            String requestId,
            Exception ex
    ) {
        String effectiveRequestId = normalizeRequestId(requestId);
        log.warn("AI chat stream fallback triggered: requestId={}, errorType={}",
                effectiveRequestId, ex.getClass().getSimpleName());
        try {
            downstream.write(streamErrorEvent(effectiveRequestId).getBytes(StandardCharsets.UTF_8));
            downstream.flush();
        } catch (IOException ignored) {
            // downstream already closed
        }
    }

    private void fallbackProxyChatStreamWithRun(
            AgentChatStreamPayload request,
            OutputStream downstream,
            String authorization,
            String requestId,
            Long runId,
            Exception ex
    ) {
        String effectiveRequestId = normalizeRequestId(requestId);
        log.warn("AI chat stream fallback triggered: requestId={}, runId={}, errorType={}",
                effectiveRequestId, runId, ex.getClass().getSimpleName());
        try {
            downstream.write(streamErrorEvent(effectiveRequestId, runId).getBytes(StandardCharsets.UTF_8));
            downstream.flush();
        } catch (IOException ignored) {
            // downstream already closed
        }
    }
}
