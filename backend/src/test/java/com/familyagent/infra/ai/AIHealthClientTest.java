package com.familyagent.infra.ai;

import com.familyagent.infra.ai.dto.AIHealthResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AIHealthClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final AIClientRequestSupport support = new AIClientRequestSupport(
            "http://ai-service",
            "internal-token",
            new SimpleMeterRegistry());
    private final AIHealthClient client = new AIHealthClient(restTemplate, support);

    @Test
    void healthCheckShouldDeserializeTypedResponse() {
        AIHealthResponse expected = new AIHealthResponse(
                "healthy",
                "familyagent-ai",
                "0.1.0",
                "prod",
                12.5,
                "dashscope/qwen-flash",
                null);
        when(restTemplate.getForObject(
                "http://ai-service/ai/health",
                AIHealthResponse.class)).thenReturn(expected);

        AIHealthResponse response = client.healthCheck();

        assertEquals(expected, response);
    }

    @Test
    void healthCheckShouldMapPythonSnakeCaseContract() {
        RestTemplate actualRestTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(actualRestTemplate);
        server.expect(requestTo("http://ai-service/ai/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {
                          "status":"healthy",
                          "service":"familyagent-ai",
                          "version":"0.1.0",
                          "environment":"prod",
                          "uptime_seconds":12.5,
                          "default_model":"dashscope/qwen-flash"
                        }
                        """,
                        MediaType.APPLICATION_JSON));
        AIHealthClient actualClient = new AIHealthClient(actualRestTemplate, support);

        AIHealthResponse response = actualClient.healthCheck();

        assertEquals(12.5, response.uptimeSeconds());
        assertEquals("dashscope/qwen-flash", response.defaultModel());
        server.verify();
    }

    @Test
    void healthCheckShouldReturnTypedDownResponseOnFailure() {
        when(restTemplate.getForObject(
                "http://ai-service/ai/health",
                AIHealthResponse.class)).thenThrow(new RuntimeException("private upstream detail"));

        AIHealthResponse response = client.healthCheck();

        assertEquals(AIHealthResponse.STATUS_DOWN, response.status());
        assertEquals(AIClientRequestSupport.ERROR_AI_SERVICE, response.errorCode());
    }
}
