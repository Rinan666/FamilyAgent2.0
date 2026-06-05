package com.familyagent.infra.ai;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
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
 * AI服务客户端 — 调用Python FastAPI服务
 */
@Slf4j
@Component
public class AIServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AIServiceClient(@Value("${ai-service.base-url:http://localhost:8000}") String baseUrl,
                           @Value("${ai-service.timeout:60}") int timeout) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 讲题 — SSE流式代理
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
                            new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI服务响应异常: " + responseCode));
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
                log.error("讲题SSE流代理异常", e);
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
     * 批改 — 同步调用
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
            log.error("批改服务调用失败", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "批改服务异常: " + e.getMessage());
        }
    }

    /**
     * AI出题 — 同步调用
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
            log.error("出题服务调用失败", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "出题服务异常: " + e.getMessage());
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

    /**
     * BKT知识追踪更新 — 调用Python BKT引擎
     * Python是BKT算法的唯一权威来源
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateBKT(double priorMastery, boolean isCorrect, int daysSinceLast) {
        try {
            Map<String, Object> request = Map.of(
                "prior_mastery", priorMastery,
                "is_correct", isCorrect,
                "days_since_last", daysSinceLast
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/ai/assessment/bkt/update", entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("BKT更新失败, 降级为简单更新", e);
            // 降级：简单地根据正确/错误调整 (fallback when AI service is down)
            double posterior = isCorrect
                ? Math.min(0.99, priorMastery + 0.1)
                : Math.max(0.01, priorMastery - 0.1);
            return Map.of(
                "success", true,
                "prior_mastery", priorMastery,
                "posterior_mastery", posterior,
                "mastery_level", posterior < 0.3 ? "弱" : posterior < 0.6 ? "中" : posterior < 0.85 ? "强" : "精通",
                "delta", posterior - priorMastery,
                "fallback", true
            );
        }
    }

    /**
     * 健康检查
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> healthCheck() {
        try {
            return restTemplate.getForObject(baseUrl + "/ai/health", Map.class);
        } catch (Exception e) {
            log.error("AI服务健康检查失败", e);
            return Map.of("status", "unhealthy", "error", e.getMessage());
        }
    }
}
