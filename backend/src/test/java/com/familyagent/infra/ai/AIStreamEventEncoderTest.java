package com.familyagent.infra.ai;

import com.familyagent.infra.ai.dto.AIStreamErrorEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIStreamEventEncoderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void errorEventShouldPreserveContractAndEscapeRequestId() throws Exception {
        byte[] encoded = AIStreamEventEncoder.encode(
                objectMapper,
                AIStreamErrorEvent.unavailable("request-\"quoted\"\nline", 91L));
        String frame = new String(encoded, StandardCharsets.UTF_8);
        JsonNode payload = objectMapper.readTree(frame.substring("data: ".length()).trim());

        assertEquals("error", payload.path("type").asText());
        assertTrue(payload.path("error").asBoolean());
        assertEquals("AI_STREAM_UNAVAILABLE", payload.path("code").asText());
        assertTrue(payload.path("retryable").asBoolean());
        assertFalse(payload.path("degraded").asBoolean());
        assertEquals("request-\"quoted\"\nline", payload.path("requestId").asText());
        assertEquals(91L, payload.path("runId").asLong());
        assertTrue(frame.endsWith("\n\n"));
    }

    @Test
    void errorEventShouldOmitAbsentRunId() throws Exception {
        byte[] encoded = AIStreamEventEncoder.encode(
                objectMapper,
                AIStreamErrorEvent.unavailable("request-1", null));
        JsonNode payload = objectMapper.readTree(
                new String(encoded, StandardCharsets.UTF_8).substring("data: ".length()).trim());

        assertFalse(payload.has("runId"));
    }
}
