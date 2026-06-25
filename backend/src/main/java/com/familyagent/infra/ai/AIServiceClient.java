package com.familyagent.infra.ai;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.dto.EmbeddingRequest;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.infra.ai.dto.MemoryExtractionRequest;
import com.familyagent.infra.ai.dto.MemoryExtractionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
    private static final String STREAM_ERROR_UNAVAILABLE = """
            data: {"type":"error","error":true,"code":"AI_STREAM_UNAVAILABLE","message":"AI service unavailable, please retry later.","retryable":true,"degraded":false}

            """;

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;
    private final ObjectMapper objectMapper;

    public AIServiceClient(@Qualifier("aiServiceRestTemplate") RestTemplate restTemplate,
                           @Value("${ai-service.base-url:http://localhost:8000}") String baseUrl,
                           @Value("${ai-service.internal-token:}") String internalToken,
                           ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
    }

    /**
     * Proxy FamilyAgent chat SSE streams from the AI service.
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackProxyChatStream")
    public void proxyChatStream(Map<String, Object> request, OutputStream downstream, String authorization) {
        try {
            String jsonBody = objectMapper.writeValueAsString(request);
            URI uri = URI.create(baseUrl + "/ai/agent/chat/stream");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.TEXT_EVENT_STREAM));
            if (authorization != null && !authorization.isBlank()) {
                headers.set("Authorization", authorization);
            }
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            restTemplate.execute(uri, HttpMethod.POST, httpRequest -> writeStreamRequest(httpRequest, entity, jsonBody), response -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    String errorBody = readResponseBody(response);
                    log.warn("AI service response error: status={}, body={}", response.getStatusCode().value(), truncateForLog(errorBody));
                    throw new BusinessException(
                            ErrorCode.AI_SERVICE_ERROR,
                            "AI service unavailable, please retry later."
                    );
                }
                copyStream(response.getBody(), downstream);
                return null;
            });
        } catch (BusinessException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            log.warn("AI service response error: status={}, body={}", e.getStatusCode().value(), truncateForLog(e.getResponseBodyAsString()));
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service unavailable, please retry later.");
        } catch (Exception e) {
            log.error("FamilyAgent chat SSE proxy failed", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service unavailable, please retry later.");
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

    private String readResponseBody(ClientHttpResponse response) {
        InputStream errorStream;
        try {
            errorStream = response.getBody();
        } catch (IOException e) {
            log.warn("Failed to open AI service error body", e);
            return "";
        }
        if (errorStream == null) {
            return "";
        }

        try (InputStream input = errorStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read AI service error body", e);
            return "";
        }
    }

    private String truncateForLog(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    /**
     * Extract memory candidates from a finished Agent session.
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackExtractMemories")
    @Retry(name = "aiService")
    public MemoryExtractionResponse extractMemories(MemoryExtractionRequest request, String authorization) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authorization != null && !authorization.isBlank()) {
                headers.set("Authorization", authorization);
            }
            HttpEntity<MemoryExtractionRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<MemoryExtractionResponse> response = restTemplate.postForEntity(
                    baseUrl + "/ai/memory/extract", entity, MemoryExtractionResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Memory extraction transport failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service unavailable");
        }
    }

    /**
     * Generate an embedding vector for backend-owned memory indexing.
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackEmbedText")
    @Retry(name = "aiService")
    public EmbeddingResponse embedText(EmbeddingRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalToken != null && !internalToken.isBlank()) {
                headers.set(INTERNAL_SERVICE_TOKEN_HEADER, internalToken);
            }
            HttpEntity<EmbeddingRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<EmbeddingResponse> response = restTemplate.postForEntity(
                    baseUrl + "/ai/embedding/embed", entity, EmbeddingResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Embedding service transport failed: {}", e.getMessage());
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
            log.error("AI service health check failed", e);
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }

    // --- Fallback methods ---

    private EmbeddingResponse fallbackEmbedText(EmbeddingRequest request, Exception ex) {
        log.warn("AI embedding fallback triggered: {}", ex.getMessage());
        return EmbeddingResponse.unavailable();
    }

    private MemoryExtractionResponse fallbackExtractMemories(MemoryExtractionRequest request, String authorization, Exception ex) {
        log.warn("AI memory extraction fallback triggered: {}", ex.getMessage());
        return MemoryExtractionResponse.unavailable();
    }

    private void fallbackProxyChatStream(Map<String, Object> request, OutputStream downstream, String authorization, Exception ex) {
        log.warn("AI chat stream fallback triggered: {}", ex.getMessage());
        try {
            downstream.write(STREAM_ERROR_UNAVAILABLE.getBytes(StandardCharsets.UTF_8));
            downstream.flush();
        } catch (IOException ignored) {
            // downstream already closed
        }
    }
}
