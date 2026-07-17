package com.familyagent.infra.ai;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.dto.AgentChatStreamPayload;
import com.familyagent.infra.ai.dto.AIStreamErrorEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class AIChatStreamClient {

    private static final int STREAM_BUFFER_SIZE = 1024;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AIClientRequestSupport support;

    public AIChatStreamClient(
            @Qualifier("aiServiceStreamRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            AIClientRequestSupport support) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.support = support;
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackProxyChatStream")
    public void proxyChatStream(
            AgentChatStreamPayload request,
            OutputStream downstream,
            String authorization,
            String requestId) {
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
        String effectiveRequestId = support.normalizeRequestId(requestId);
        boolean success = false;
        String errorCode = AIClientRequestSupport.ERROR_NONE;
        try {
            String jsonBody = objectMapper.writeValueAsString(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.TEXT_EVENT_STREAM));
            support.addInternalHeaders(headers, effectiveRequestId, runId);
            if (authorization != null && !authorization.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
            }
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            restTemplate.execute(
                    URI.create(support.url("/ai/agent/chat/stream")),
                    HttpMethod.POST,
                    httpRequest -> writeRequest(httpRequest, entity, jsonBody),
                    response -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            log.warn("AI service stream response error: requestId={}, status={}",
                                    effectiveRequestId, response.getStatusCode().value());
                            throw unavailable();
                        }
                        copyStream(response.getBody(), downstream);
                        return null;
                    });
            success = true;
        } catch (BusinessException error) {
            errorCode = AIClientRequestSupport.ERROR_AI_SERVICE;
            throw error;
        } catch (HttpStatusCodeException error) {
            errorCode = AIClientRequestSupport.ERROR_AI_SERVICE;
            log.warn("AI service stream response error: requestId={}, status={}",
                    effectiveRequestId, error.getStatusCode().value());
            throw unavailable();
        } catch (Exception error) {
            errorCode = "AI_STREAM_INTERRUPTED";
            log.error("FamilyAgent chat SSE proxy failed: requestId={}, errorType={}",
                    effectiveRequestId, error.getClass().getSimpleName());
            throw unavailable();
        } finally {
            support.record(
                    "chat_stream",
                    success,
                    errorCode,
                    null,
                    null,
                    false,
                    support.elapsed(startedAt));
        }
    }

    private static void writeRequest(
            ClientHttpRequest request,
            HttpEntity<String> entity,
            String jsonBody) throws IOException {
        request.getHeaders().putAll(entity.getHeaders());
        request.getBody().write(jsonBody.getBytes(StandardCharsets.UTF_8));
        request.getBody().flush();
    }

    private static void copyStream(InputStream upstream, OutputStream downstream) throws IOException {
        byte[] buffer = new byte[STREAM_BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = upstream.read(buffer)) != -1) {
            downstream.write(buffer, 0, bytesRead);
            downstream.flush();
        }
    }

    private void fallbackProxyChatStream(
            AgentChatStreamPayload request,
            OutputStream downstream,
            String authorization,
            String requestId,
            Exception error) {
        writeFallback(downstream, requestId, null, error);
    }

    private void fallbackProxyChatStreamWithRun(
            AgentChatStreamPayload request,
            OutputStream downstream,
            String authorization,
            String requestId,
            Long runId,
            Exception error) {
        writeFallback(downstream, requestId, runId, error);
    }

    private void writeFallback(OutputStream downstream, String requestId, Long runId, Exception error) {
        String effectiveRequestId = support.normalizeRequestId(requestId);
        log.warn("AI chat stream fallback triggered: requestId={}, runId={}, errorType={}",
                effectiveRequestId, runId, error.getClass().getSimpleName());
        try {
            downstream.write(AIStreamEventEncoder.encode(
                    objectMapper,
                    AIStreamErrorEvent.unavailable(effectiveRequestId, runId)));
            downstream.flush();
        } catch (IOException ignored) {
            // Downstream is already closed.
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service unavailable, please retry later.");
    }
}
