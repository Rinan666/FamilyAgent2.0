package com.familyagent.infra.ai;

import com.familyagent.infra.ai.dto.AgentChatStreamPayload;
import com.familyagent.infra.ai.dto.EmbeddingRequest;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.infra.ai.dto.MemoryExtractionRequest;
import com.familyagent.infra.ai.dto.MemoryExtractionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        return new AIServiceClient(
                new RestTemplateBuilder().requestFactory(() -> requestFactory).build(),
                new RestTemplateBuilder().requestFactory(() -> requestFactory).build(),
                baseUrl(),
                internalToken,
                objectMapper,
                meterRegistry);
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
    void extractMemories_shouldParseTypedResponseAndSendAuthorization() throws Exception {
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        server = startServer("/ai/memory/extract", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, "application/json", 200,
                    "{\"success\":true,\"deprecated\":true,\"degraded\":false,\"memories\":[{\"type\":\"LEARNING\",\"content\":\"孩子先复述题意再画图更稳定。\",\"summary\":\"应用题策略\",\"importance\":4,\"confidence\":0.8}],\"message\":\"ok\"}");
        });

        AIServiceClient client = createClient("secret-token");

        MemoryExtractionResponse response = client.extractMemories(MemoryExtractionRequest.builder()
                .sessionId(7L)
                .subject("FamilyAgent")
                .messages(java.util.List.of(MemoryExtractionRequest.Message.builder()
                        .role("user")
                        .content("孩子做应用题先复述题意会更稳定")
                        .build()))
                .summary("应用题学习策略")
                .build(), "Bearer demo-token");

        assertEquals("Bearer demo-token", authorizationHeader.get());
        assertEquals(true, response.isSuccess());
        assertEquals(true, response.isDeprecated());
        assertEquals(1, response.getMemories().size());
        assertEquals("LEARNING", response.getMemories().get(0).getType());
        assertEquals(0.8, response.getMemories().get(0).getConfidence());
    }

    @Test
    void extractMemories_shouldParseSnakeCaseErrorCodeAndRecordBusinessFailure() throws Exception {
        server = startServer("/ai/memory/extract", exchange -> {
            respond(exchange, "application/json", 200,
                    "{\"success\":false,\"deprecated\":true,\"degraded\":false,\"memories\":[],\"error_code\":\"MEMORY_EXTRACT_DEPRECATED\",\"message\":\"deprecated\"}");
        });

        AIServiceClient client = createClient("secret-token");

        MemoryExtractionResponse response = client.extractMemories(MemoryExtractionRequest.builder()
                .sessionId(7L)
                .subject("FamilyAgent")
                .messages(java.util.List.of(MemoryExtractionRequest.Message.builder()
                        .role("user")
                        .content("孩子做应用题先复述题意会更稳定")
                        .build()))
                .summary("应用题学习策略")
                .build(), "Bearer demo-token");

        assertEquals(false, response.isSuccess());
        assertEquals("MEMORY_EXTRACT_DEPRECATED", response.getErrorCode());
        assertEquals(1, meterRegistry.find("familyagent.ai.client.request")
                .tag("operation", "memory_extract")
                .tag("success", "false")
                .tag("errorCode", "MEMORY_EXTRACT_DEPRECATED")
                .tag("degraded", "false")
                .timer()
                .count());
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
        AtomicReference<String> requestIdHeader = new AtomicReference<>();
        server = startServer("/ai/agent/chat/stream", exchange -> {
            acceptHeader.set(exchange.getRequestHeaders().getFirst("Accept"));
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestIdHeader.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            respond(exchange, "text/event-stream", 200, ": connected\n\ndata: {\"content\":\"hello\"}\n\ndata: {\"done\":true}\n\n");
        });

        AIServiceClient client = createClient("secret-token");
        ByteArrayOutputStream downstream = new ByteArrayOutputStream();

        client.proxyChatStream(streamPayload("tell me one thing"), downstream, "Bearer demo-token", "chat-test-request");

        assertEquals("text/event-stream", acceptHeader.get());
        assertEquals("Bearer demo-token", authorizationHeader.get());
        assertEquals("chat-test-request", requestIdHeader.get());
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
    void proxyChatStream_shouldThrowOnNon200InsteadOfWritingAssistantText() throws Exception {
        server = startServer("/ai/agent/chat/stream", exchange -> {
            respond(exchange, "application/json", 503, "{\"detail\":\"provider unavailable\"}");
        });

        AIServiceClient client = createClient("secret-token");
        ByteArrayOutputStream downstream = new ByteArrayOutputStream();

        assertThrows(RuntimeException.class, () ->
                client.proxyChatStream(streamPayload("tell me one thing"), downstream, "Bearer demo-token", "chat-error-request"));

        assertEquals("", downstream.toString(StandardCharsets.UTF_8));
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
