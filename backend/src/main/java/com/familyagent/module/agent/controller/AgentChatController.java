package com.familyagent.module.agent.controller;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.agent.dto.AgentChatStreamRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Primary FamilyAgent chat stream endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/chat")
@RequiredArgsConstructor
public class AgentChatController {

    private static final int MAX_CHATS_PER_HOUR = 20;
    private static final int MAX_CHAT_REQUEST_BYTES = 32 * 1024;
    private static final DateTimeFormatter HOUR_KEY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String STREAM_ERROR_UNAVAILABLE = """
            data: {"type":"error","error":true,"code":"AI_STREAM_UNAVAILABLE","message":"AI service unavailable, please retry later.","retryable":true,"degraded":false}

            """;

    private final AIServiceClient aiServiceClient;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Proxy FamilyAgent chat stream")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void stream(@Valid @RequestBody AgentChatStreamRequest request,
                       @RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
                       HttpServletResponse response) throws IOException {
        Long userId = CurrentUserGuard.currentUserId();
        String effectiveRequestId = normalizeRequestId(requestId);
        Map<String, Object> aiPayload = request.toAiPayload();
        enforceRequestSize(aiPayload);
        String hourKey = "quota:chat:user:" + userId + ":" + LocalDateTime.now().format(HOUR_KEY_FMT);
        RAtomicLong counter = redissonClient.getAtomicLong(hourKey);
        long count = counter.incrementAndGet();
        if (count == 1) {
            counter.expire(1, TimeUnit.HOURS);
        } else if (count > MAX_CHATS_PER_HOUR) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED,
                    "每小时对话次数已达上限（" + MAX_CHATS_PER_HOUR + " 次），请稍后再试");
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader(REQUEST_ID_HEADER, effectiveRequestId);

        try (OutputStream outputStream = response.getOutputStream()) {
            aiServiceClient.proxyChatStream(aiPayload, outputStream, authorization, effectiveRequestId);
        } catch (BusinessException e) {
            if (!response.isCommitted()) {
                response.resetBuffer();
                response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                response.getWriter().write(String.format(
                        "{\"code\":%d,\"message\":%s}",
                        ErrorCode.AI_SERVICE_ERROR.getCode(),
                        toJsonString(e.getMessage())
                ));
                response.flushBuffer();
                return;
            }

            log.warn("FamilyAgent chat stream failed after response committed: requestId={}, error={}", effectiveRequestId, e.getMessage());
            writeErrorEvent(response.getOutputStream(), effectiveRequestId);
        }
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return "chat-" + UUID.randomUUID();
        }
        String trimmed = requestId.trim();
        return trimmed.length() <= 128 ? trimmed : trimmed.substring(0, 128);
    }

    private void enforceRequestSize(Map<String, Object> request) {
        try {
            int bytes = objectMapper.writeValueAsBytes(request == null ? Map.of() : request).length;
            if (bytes > MAX_CHAT_REQUEST_BYTES) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Chat request is too large");
            }
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Chat request could not be processed");
        }
    }

    private void writeErrorEvent(OutputStream outputStream, String requestId) {
        try {
            outputStream.write(streamErrorEvent(requestId).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException ioException) {
            log.warn("Failed to send downstream SSE error event", ioException);
        }
    }

    private String streamErrorEvent(String requestId) {
        return "data: {\"type\":\"error\",\"error\":true,\"code\":\"AI_STREAM_UNAVAILABLE\","
                + "\"message\":\"AI service unavailable, please retry later.\",\"retryable\":true,"
                + "\"degraded\":false,\"requestId\":\"" + escapeJson(normalizeRequestId(requestId)) + "\"}\n\n";
    }

    private String escapeJson(String value) {
        String text = value == null ? "" : value;
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String toJsonString(String value) {
        String text = value == null ? "" : value;
        return '"' + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + '"';
    }
}
