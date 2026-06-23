package com.familyagent.infra.wechat;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeChatMiniAppClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void exchangeCodeForSession_shouldReturnOpenId() throws Exception {
        server = startServer(exchange -> respond(exchange, 200,
                "{\"openid\":\"openid-123\",\"session_key\":\"session-456\"}"));
        WeChatMiniAppClient client = createClient();

        WeChatMiniAppClient.SessionInfo sessionInfo = client.exchangeCodeForSession("demo-code");

        assertEquals("openid-123", sessionInfo.openId());
        assertEquals("session-456", sessionInfo.sessionKey());
    }

    @Test
    void exchangeCodeForSession_shouldTranslateWeChatErrors() throws Exception {
        server = startServer(exchange -> respond(exchange, 200,
                "{\"errcode\":40029,\"errmsg\":\"invalid code\"}"));
        WeChatMiniAppClient client = createClient();

        BusinessException error = assertThrows(BusinessException.class,
                () -> client.exchangeCodeForSession("bad-code"));

        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), error.getCode());
    }

    private WeChatMiniAppClient createClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(5_000);
        RestTemplate restTemplate = new RestTemplateBuilder().requestFactory(() -> requestFactory).build();
        return new WeChatMiniAppClient(restTemplate, "demo-app-id", "demo-secret",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/sns/jscode2session");
    }

    private HttpServer startServer(ExchangeHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/sns/jscode2session", handler::handle);
        httpServer.start();
        return httpServer;
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
