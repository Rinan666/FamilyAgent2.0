package com.familyagent.infra.ai;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.dto.SaveMemoryPlanPayload;
import com.familyagent.infra.ai.dto.SaveMemoryPlanResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SaveMemoryPlanClient {

    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String RUN_ID_HEADER = "X-Agent-Run-Id";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public SaveMemoryPlanClient(
            @Qualifier("aiServiceRestTemplate") RestTemplate restTemplate,
            @Value("${ai-service.base-url:http://localhost:8000}") String baseUrl,
            @Value("${ai-service.internal-token:}") String internalToken,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @CircuitBreaker(name = "aiService")
    @Retry(name = "aiService", fallbackMethod = "fallbackPlanSaveMemory")
    public SaveMemoryPlanResponse plan(SaveMemoryPlanPayload payload, String requestId) {
        return planInternal(payload, requestId, null);
    }

    @CircuitBreaker(name = "aiService")
    @Retry(name = "aiService", fallbackMethod = "fallbackPlanSaveMemoryWithRun")
    public SaveMemoryPlanResponse plan(
            SaveMemoryPlanPayload payload,
            String requestId,
            Long runId) {
        return planInternal(payload, requestId, runId);
    }

    private SaveMemoryPlanResponse planInternal(
            SaveMemoryPlanPayload payload,
            String requestId,
            Long runId) {
        long startedAt = System.nanoTime();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(REQUEST_ID_HEADER, requestId);
            if (runId != null) {
                headers.set(RUN_ID_HEADER, String.valueOf(runId));
            }
            if (internalToken != null && !internalToken.isBlank()) {
                headers.set(INTERNAL_SERVICE_TOKEN_HEADER, internalToken);
            }
            ResponseEntity<SaveMemoryPlanResponse> response = restTemplate.postForEntity(
                    baseUrl + "/ai/memory/save-plan",
                    new HttpEntity<>(payload, headers),
                    SaveMemoryPlanResponse.class);
            SaveMemoryPlanResponse body = response.getBody();
            if (body == null) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service returned an empty response");
            }
            record(body.isSuccess(), body.getErrorCode(), elapsed(startedAt));
            return body;
        } catch (HttpStatusCodeException error) {
            record(false, "AI_HTTP_" + error.getStatusCode().value(), elapsed(startedAt));
            if (error.getStatusCode().value() == 400) {
                throw new AIServiceInputRejectedException(errorDetail(error));
            }
            if (error.getStatusCode().value() == 429) {
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "AI 请求过于频繁，请稍后重试");
            }
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service unavailable");
        } catch (AIServiceInputRejectedException | BusinessException error) {
            throw error;
        } catch (Exception error) {
            record(false, "AI_SERVICE_ERROR", elapsed(startedAt));
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service unavailable");
        }
    }

    private SaveMemoryPlanResponse fallbackPlanSaveMemory(
            SaveMemoryPlanPayload payload,
            String requestId,
            Exception error) {
        log.warn("Save-memory planning fallback triggered: requestId={}, errorType={}",
                requestId, error.getClass().getSimpleName());
        return SaveMemoryPlanResponse.unavailable();
    }

    private SaveMemoryPlanResponse fallbackPlanSaveMemoryWithRun(
            SaveMemoryPlanPayload payload,
            String requestId,
            Long runId,
            Exception error) {
        log.warn("Save-memory planning fallback triggered: requestId={}, runId={}, errorType={}",
                requestId, runId, error.getClass().getSimpleName());
        return SaveMemoryPlanResponse.unavailable();
    }

    private String errorDetail(HttpStatusCodeException error) {
        try {
            JsonNode detail = objectMapper.readTree(error.getResponseBodyAsString()).path("detail");
            String value = detail.isTextual() ? detail.asText().trim() : "";
            return value.isEmpty() ? "AI 请求不符合安全规则" : value.substring(0, Math.min(value.length(), 200));
        } catch (Exception ignored) {
            return "AI 请求不符合安全规则";
        }
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private void record(boolean success, String errorCode, Duration duration) {
        meterRegistry.timer(
                "familyagent.ai.client.request",
                "operation", "save_memory_plan",
                "success", Boolean.toString(success),
                "errorCode", errorCode == null || errorCode.isBlank() ? "NONE" : errorCode,
                "provider", "none",
                "model", "none",
                "degraded", "false"
        ).record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }
}
