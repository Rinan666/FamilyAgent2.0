package com.familyagent.infra.ai;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
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

    private static final int MAX_CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int STREAM_TIMEOUT_MILLIS = 300_000;
    private static final int STREAM_BUFFER_SIZE = 1024;
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

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
    public void proxyChatStream(Map<String, Object> request, OutputStream downstream, String authorization) {
        HttpURLConnection conn = null;
        try {
            String jsonBody = objectMapper.writeValueAsString(request);

            URI uri = URI.create(baseUrl + "/ai/agent/chat/stream");
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            conn.setRequestProperty("Accept", "text/event-stream");
            if (authorization != null && !authorization.isBlank()) {
                conn.setRequestProperty("Authorization", authorization);
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(MAX_CONNECT_TIMEOUT_MILLIS);
            conn.setReadTimeout(STREAM_TIMEOUT_MILLIS);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorBody = readResponseBody(conn);
                throw new BusinessException(
                        ErrorCode.AI_SERVICE_ERROR,
                        "AI service response error: " + responseCode + (errorBody.isBlank() ? "" : " - " + errorBody)
                );
            }

            try (InputStream upstream = conn.getInputStream()) {
                byte[] buffer = new byte[STREAM_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = upstream.read(buffer)) != -1) {
                    downstream.write(buffer, 0, bytesRead);
                    downstream.flush();
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("FamilyAgent chat SSE proxy failed", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "FamilyAgent chat SSE proxy failed: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String readResponseBody(HttpURLConnection conn) {
        InputStream errorStream = conn.getErrorStream();
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

    /**
     * Extract memory candidates from a finished Agent session.
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
            if (internalToken != null && !internalToken.isBlank()) {
                headers.set(INTERNAL_SERVICE_TOKEN_HEADER, internalToken);
            }
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
     * Summarize a session archive chunk via the AI service using the internal service token.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> summarizeSessionArchive(Map<String, Object> request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalToken != null && !internalToken.isBlank()) {
                headers.set(INTERNAL_SERVICE_TOKEN_HEADER, internalToken);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/ai/memory/session-archive-summary", entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Session archive summary call failed: {}", e.getMessage());
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
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }
}
