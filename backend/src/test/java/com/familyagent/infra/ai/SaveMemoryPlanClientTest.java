package com.familyagent.infra.ai;

import com.familyagent.infra.ai.dto.SaveMemoryPlanPayload;
import com.familyagent.infra.ai.dto.SaveMemoryPlanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaveMemoryPlanClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final SaveMemoryPlanClient client = new SaveMemoryPlanClient(
            restTemplate,
            "http://ai-service",
            "internal-token",
            new ObjectMapper(),
            meterRegistry);

    @Test
    void planSendsInternalHeadersAndReturnsTypedResponse() {
        SaveMemoryPlanResponse response = new SaveMemoryPlanResponse();
        response.setSuccess(true);
        when(restTemplate.postForEntity(
                eq("http://ai-service/ai/memory/save-plan"),
                any(HttpEntity.class),
                eq(SaveMemoryPlanResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
        SaveMemoryPlanPayload payload = payload();

        SaveMemoryPlanResponse result = client.plan(payload, "save-plan-request", 91L);

        assertEquals(response, result);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<HttpEntity<SaveMemoryPlanPayload>> captor =
                org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("http://ai-service/ai/memory/save-plan"),
                captor.capture(),
                eq(SaveMemoryPlanResponse.class));
        assertEquals("internal-token", captor.getValue().getHeaders().getFirst("X-Internal-Service-Token"));
        assertEquals("save-plan-request", captor.getValue().getHeaders().getFirst("X-Request-Id"));
        assertEquals("91", captor.getValue().getHeaders().getFirst("X-Agent-Run-Id"));
        assertEquals(1, meterRegistry.find("familyagent.ai.client.request")
                .tag("operation", "save_memory_plan")
                .tag("success", "true")
                .timer()
                .count());
    }

    @Test
    void planPreservesSafeInputGuardMessageWithoutRetryableBusinessError() {
        HttpClientErrorException error = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                "{\"detail\":\"内容疑似低俗暗语\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(SaveMemoryPlanResponse.class)))
                .thenThrow(error);

        AIServiceInputRejectedException result = assertThrows(
                AIServiceInputRejectedException.class,
                () -> client.plan(payload(), "save-plan-request"));

        assertEquals("内容疑似低俗暗语", result.getMessage());
    }

    private SaveMemoryPlanPayload payload() {
        return new SaveMemoryPlanPayload(
                "保存这段经历",
                "测试家庭",
                List.of(new SaveMemoryPlanPayload.ConversationMessage("user", "具体家庭经历")),
                "孩子",
                "PARENT");
    }
}
