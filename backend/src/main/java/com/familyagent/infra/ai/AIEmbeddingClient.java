package com.familyagent.infra.ai;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.dto.EmbeddingRequest;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class AIEmbeddingClient {

    private final RestTemplate restTemplate;
    private final AIClientRequestSupport support;

    public AIEmbeddingClient(
            @Qualifier("aiServiceRestTemplate") RestTemplate restTemplate,
            AIClientRequestSupport support) {
        this.restTemplate = restTemplate;
        this.support = support;
    }

    @CircuitBreaker(name = "aiService")
    @Retry(name = "aiService", fallbackMethod = "fallbackEmbedText")
    public EmbeddingResponse embedText(EmbeddingRequest request) {
        long startedAt = System.nanoTime();
        String requestId = support.newRequestId();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            support.addInternalHeaders(headers, requestId, null);
            ResponseEntity<EmbeddingResponse> response = restTemplate.postForEntity(
                    support.url("/ai/embedding/embed"),
                    new HttpEntity<>(request, headers),
                    EmbeddingResponse.class);
            EmbeddingResponse body = response.getBody();
            boolean success = body != null && body.isSuccess();
            support.record(
                    "embedding",
                    success,
                    support.metricErrorCode(success, body != null ? body.getErrorCode() : "AI_EMPTY_RESPONSE"),
                    body != null ? body.getProvider() : null,
                    body != null ? body.getModel() : null,
                    body != null && body.isDegraded(),
                    support.elapsed(startedAt));
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
        } catch (Exception error) {
            support.record(
                    "embedding",
                    false,
                    AIClientRequestSupport.ERROR_AI_SERVICE,
                    null,
                    null,
                    false,
                    support.elapsed(startedAt));
            log.warn("Embedding service transport failed: requestId={}, errorType={}",
                    requestId, error.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service unavailable");
        }
    }

    private EmbeddingResponse fallbackEmbedText(EmbeddingRequest request, Exception error) {
        log.warn("AI embedding fallback triggered: errorType={}", error.getClass().getSimpleName());
        return EmbeddingResponse.unavailable();
    }
}
