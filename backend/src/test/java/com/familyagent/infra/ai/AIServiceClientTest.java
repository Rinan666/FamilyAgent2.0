package com.familyagent.infra.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIServiceClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void embedText_shouldSendInternalServiceToken() throws Exception {
        AtomicReference<String> receivedToken = new AtomicReference<>();
        server = startServer("/ai/embedding/embed", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"));
            respond(exchange, "application/json", 200,
                    "{\"success\":true,\"embedding\":[0.1,0.2],\"model\":\"local/test\",\"privacy_categories\":[]}");
        });

        AIServiceClient client = new AIServiceClient(baseUrl(), 5, "secret-token");

        Map<String, Object> response = client.embedText(Map.of("text", "family memory", "dimensions", 1536));

        assertEquals("secret-token", receivedToken.get());
        assertEquals(Boolean.TRUE, response.get("success"));
    }

    @Test
    void embedText_shouldNotSendBlankInternalServiceToken() throws Exception {
        AtomicReference<String> receivedToken = new AtomicReference<>();
        server = startServer("/ai/embedding/embed", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"));
            respond(exchange, "application/json", 200,
                    "{\"success\":true,\"embedding\":[0.1,0.2],\"model\":\"local/test\",\"privacy_categories\":[]}");
        });

        AIServiceClient client = new AIServiceClient(baseUrl(), 5, " ");

        Map<String, Object> response = client.embedText(Map.of("text", "family memory", "dimensions", 1536));

        assertEquals(null, receivedToken.get());
        assertEquals(Boolean.TRUE, response.get("success"));
    }

    @Test
    void proxyChatStream_shouldForwardRawSseFramesAndHeaders() throws Exception {
        AtomicReference<String> acceptHeader = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        server = startServer("/ai/agent/chat/stream", exchange -> {
            acceptHeader.set(exchange.getRequestHeaders().getFirst("Accept"));
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, "text/event-stream", 200, ": connected\n\ndata: {\"content\":\"hello\"}\n\ndata: {\"done\":true}\n\n");
        });

        AIServiceClient client = new AIServiceClient(baseUrl(), 5, "secret-token");
        ByteArrayOutputStream downstream = new ByteArrayOutputStream();

        client.proxyChatStream(Map.of("member_message", "tell me one thing"), downstream, "Bearer demo-token");

        assertEquals("text/event-stream", acceptHeader.get());
        assertEquals("Bearer demo-token", authorizationHeader.get());
        assertEquals(": connected\n\ndata: {\"content\":\"hello\"}\n\ndata: {\"done\":true}\n\n",
                downstream.toString(StandardCharsets.UTF_8));
    }

    @Test
    void summarizeSessionArchive_shouldSendInternalServiceToken() throws Exception {
        AtomicReference<String> receivedToken = new AtomicReference<>();
        server = startServer("/ai/memory/session-archive-summary", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"));
            respond(exchange, "application/json", 200,
                    "{\"success\":true,\"data\":{\"summary\":\"archive summary\",\"titleSuggestion\":\"family title\",\"confidence\":\"HIGH\"}}");
        });

        AIServiceClient client = new AIServiceClient(baseUrl(), 5, "secret-token");

        Map<String, Object> response = client.summarizeSessionArchive(Map.of("session_id", 1, "messages", List.of()));

        assertEquals("secret-token", receivedToken.get());
        assertEquals(Boolean.TRUE, response.get("success"));
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
