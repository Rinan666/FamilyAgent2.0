package com.familyagent.infra.ai;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI service client for the Python FastAPI service.
 */
@Slf4j
@Component
public class AIServiceClient {

    private static final int MILLIS_PER_SECOND = 1000;
    private static final int MAX_CONNECT_TIMEOUT_MILLIS = 10_000;

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AIServiceClient(@Value("${ai-service.base-url:http://localhost:8000}") String baseUrl,
                           @Value("${ai-service.timeout:60}") int timeout) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate(createRequestFactory(timeout));
    }

    private static SimpleClientHttpRequestFactory createRequestFactory(int timeoutSeconds) {
        int readTimeoutMillis = Math.max(1, timeoutSeconds) * MILLIS_PER_SECOND;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.min(readTimeoutMillis, MAX_CONNECT_TIMEOUT_MILLIS));
        requestFactory.setReadTimeout(readTimeoutMillis);
        return requestFactory;
    }

    /**
     * Proxy tutor SSE streams from the AI service.
     */
    public SseEmitter proxyExplainStream(Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(300_000L);

        CompletableFuture.runAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request);

                URI uri = URI.create(baseUrl + "/ai/tutor/explain");
                conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(300000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    emitter.completeWithError(
                            new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service response error: " + responseCode));
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        emitter.send(SseEmitter.event().data(line));
                    }
                }
                emitter.complete();

            } catch (Exception e) {
                log.error("Tutor SSE proxy failed", e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    // ignore
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });

        return emitter;
    }

    /**
     * Call the grading endpoint synchronously.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> gradeAnswer(Map<String, Object> request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/ai/tutor/grade", entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Grading service call failed", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Grading service error: " + e.getMessage());
        }
    }

    /**
     * Call the question generation endpoint synchronously.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generateQuestions(Map<String, Object> request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/ai/tutor/generate", entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Question generation service call failed", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Question generation service error: " + e.getMessage());
        }
    }

    /**
     * Extract learning memories from a finished tutor session.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractMemories(Map<String, Object> request, String authorization) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authorization != null && !authorization.isBlank()) {
                headers.set("Authorization", authorization);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/ai/memory/extract", entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Memory extraction call failed: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> compressDiary(Map<String, Object> request, String authorization) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authorization != null && !authorization.isBlank()) {
                headers.set("Authorization", authorization);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/ai/memory/compress-diary", entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Diary compression call failed: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * Generate an embedding vector for backend-owned memory indexing.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> embedText(Map<String, Object> request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/ai/embedding/embed", entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Embedding service call failed: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
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
            return Map.of("status", "unhealthy", "error", e.getMessage());
        }
    }
}
