package com.familyagent.module.agent.controller;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.AIServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Primary FamilyAgent chat stream endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/chat")
@RequiredArgsConstructor
public class AgentChatController {

    private final AIServiceClient aiServiceClient;

    @Operation(summary = "Proxy FamilyAgent chat stream")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void stream(@RequestBody Map<String, Object> request,
                       @RequestHeader(value = "Authorization", required = false) String authorization,
                       HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        try (OutputStream outputStream = response.getOutputStream()) {
            aiServiceClient.proxyChatStream(request, outputStream, authorization);
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

            log.warn("FamilyAgent chat stream failed after response committed: {}", e.getMessage());
            writeErrorEvent(response.getOutputStream(), e.getMessage());
        }
    }

    private void writeErrorEvent(OutputStream outputStream, String message) {
        try {
            String payload = "data: {\"error\":" + toJsonString(message) + "}\n\n";
            outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException ioException) {
            log.warn("Failed to send downstream SSE error event", ioException);
        }
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
