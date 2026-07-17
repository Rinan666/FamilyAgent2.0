package com.familyagent.infra.ai;

import com.familyagent.infra.ai.dto.AgentChatStreamPayload;
import com.familyagent.infra.ai.dto.EmbeddingRequest;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.infra.ai.dto.SaveMemoryPlanPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AIServiceClientTest {

    private HttpServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AIServiceClient createClient(String internalToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(5_000);
        RestTemplate restTemplate = new RestTemplateBuilder().requestFactory(() -> requestFactory).build();
        RestTemplate streamRestTemplate = new RestTemplateBuilder().requestFactory(() -> requestFactory).build();
        AIClientRequestSupport support = new AIClientRequestSupport(baseUrl(), internalToken, meterRegistry);
        return new AIServiceClient(
                mock(SaveMemoryPlanClient.class),
                new AIChatStreamClient(streamRestTemplate, objectMapper, support),
                new AIEmbeddingClient(restTemplate, support),
                new AIHealthClient(restTemplate, support),
                support);
    }

    @Test
    void embedText_shouldSendInternalServiceToken() throws Exception {
        AtomicReference<String> receivedToken = new AtomicReference<>();
        server = startServer("/ai/embedding/embed", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"));
            respond(exchange, "application/json", 200,
                    "{\"success\":true,\"embedding\":[0.1,0.2],\"model\":\"local/test\",\"privacy_categories\":[]}");
        });

        AIServiceClient client = createClient("secret-token");

        EmbeddingResponse response = client.embedText(EmbeddingRequest.builder()
                .text("family memory")
                .dimensions(1536)
                .build());

        assertEquals("secret-token", receivedToken.get());
        assertEquals(true, response.isSuccess());
    }

    @Test
    void embedText_shouldNotSendBlankInternalServiceToken() throws Exception {
        AtomicReference<String> receivedToken = new AtomicReference<>();
        server = startServer("/ai/embedding/embed", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"));
            respond(exchange, "application/json", 200,
                    "{\"success\":true,\"embedding\":[0.1,0.2],\"model\":\"local/test\",\"privacy_categories\":[]}");
        });

        AIServiceClient client = createClient(" ");

        EmbeddingResponse response = client.embedText(EmbeddingRequest.builder()
                .text("family memory")
                .dimensions(1536)
                .build());

        assertEquals(null, receivedToken.get());
        assertEquals(true, response.isSuccess());
    }

    @Test
    void embedText_shouldSendRequestIdAndRecordObservationMetric() throws Exception {
        AtomicReference<String> requestId = new AtomicReference<>();
        server = startServer("/ai/embedding/embed", exchange -> {
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            respond(exchange, "application/json", 200,
                    "{\"success\":true,\"degraded\":false,\"provider\":\"local\",\"embedding\":[0.1,0.2],\"model\":\"local/test\",\"dimensions\":2,\"latency_ms\":12,\"request_id\":\"ai-service-request\",\"privacy_categories\":[]}");
        });

        AIServiceClient client = createClient("secret-token");

        EmbeddingResponse response = client.embedText(EmbeddingRequest.builder()
                .text("family memory")
                .dimensions(1536)
                .build());

        assertTrue(requestId.get().startsWith("ai-"));
        assertEquals("local", response.getProvider());
        assertEquals(12L, response.getLatencyMs());
        assertEquals("ai-service-request", response.getRequestId());
        assertEquals(1, meterRegistry.find("familyagent.ai.client.request")
                .tag("operation", "embedding")
                .tag("success", "true")
                .timer()
                .count());
    }

    @Test
    void proxyChatStream_shouldForwardRawSseFramesAndHeaders() throws Exception {
        AtomicReference<String> acceptHeader = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<String> internalTokenHeader = new AtomicReference<>();
        AtomicReference<String> requestIdHeader = new AtomicReference<>();
        AtomicReference<String> runIdHeader = new AtomicReference<>();
        server = startServer("/ai/agent/chat/stream", exchange -> {
            acceptHeader.set(exchange.getRequestHeaders().getFirst("Accept"));
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            internalTokenHeader.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"));
            requestIdHeader.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            runIdHeader.set(exchange.getRequestHeaders().getFirst("X-Agent-Run-Id"));
            respond(exchange, "text/event-stream", 200, ": connected\n\ndata: {\"content\":\"hello\"}\n\ndata: {\"done\":true}\n\n");
        });

        AIServiceClient client = createClient("secret-token");
        ByteArrayOutputStream downstream = new ByteArrayOutputStream();

        client.proxyChatStream(
                streamPayload("tell me one thing"),
                downstream,
                "Bearer demo-token",
                "chat-test-request",
                91L);

        assertEquals("text/event-stream", acceptHeader.get());
        assertEquals("Bearer demo-token", authorizationHeader.get());
        assertEquals("secret-token", internalTokenHeader.get());
        assertEquals("chat-test-request", requestIdHeader.get());
        assertEquals("91", runIdHeader.get());
        assertEquals(": connected\n\ndata: {\"content\":\"hello\"}\n\ndata: {\"done\":true}\n\n",
                downstream.toString(StandardCharsets.UTF_8));
        assertEquals(1, meterRegistry.find("familyagent.ai.client.request")
                .tag("operation", "chat_stream")
                .tag("success", "true")
                .tag("errorCode", "NONE")
                .tag("provider", "none")
                .tag("model", "none")
                .tag("degraded", "false")
                .timer()
                .count());
    }

    @Test
    void proxyChatStream_shouldThrowOnNon200InsteadOfWritingAssistantText(CapturedOutput output) throws Exception {
        server = startServer("/ai/agent/chat/stream", exchange -> {
            respond(exchange, "application/json", 503, "{\"detail\":\"private upstream body detail\"}");
        });

        AIServiceClient client = createClient("secret-token");
        ByteArrayOutputStream downstream = new ByteArrayOutputStream();

        assertThrows(RuntimeException.class, () ->
                client.proxyChatStream(streamPayload("tell me one thing"), downstream, "Bearer demo-token", "chat-error-request"));

        assertEquals("", downstream.toString(StandardCharsets.UTF_8));
        assertFalse(output.getAll().contains("private upstream body detail"));
    }

    @Test
    void planSaveMemory_mapsInputRejectionToBadRequest() {
        SaveMemoryPlanClient planClient = mock(SaveMemoryPlanClient.class);
        when(planClient.plan(any(), eq("request-1")))
                .thenThrow(new AIServiceInputRejectedException("内容疑似低俗暗语"));
        RestTemplate restTemplate = mock(RestTemplate.class);
        AIClientRequestSupport support = new AIClientRequestSupport("http://ai-service", "token", meterRegistry);
        AIServiceClient client = new AIServiceClient(
                planClient,
                new AIChatStreamClient(restTemplate, objectMapper, support),
                new AIEmbeddingClient(restTemplate, support),
                new AIHealthClient(restTemplate, support),
                support);

        com.familyagent.common.exception.BusinessException error = assertThrows(
                com.familyagent.common.exception.BusinessException.class,
                () -> client.planSaveMemory(
                        new SaveMemoryPlanPayload("message", "", List.of(), "", ""),
                        "request-1"));

        assertEquals(com.familyagent.common.response.ErrorCode.BAD_REQUEST.getCode(), error.getCode());
    }

    private AgentChatStreamPayload streamPayload(String message) {
        return new AgentChatStreamPayload(
                message,
                List.of(),
                "FamilyAgent",
                "family_memory",
                "",
                "MEMBER",
                "MEMBER",
                "think",
                "",
                ""
        );
    }

    private HttpServer startServer(String path, ExchangeHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext(path, exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertTrue(exchange.getRequestBody().readAllBytes().length > 0);
            handler.handle(exchange);
        });
        httpServer.start();
        return httpServer;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, String contentType, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
