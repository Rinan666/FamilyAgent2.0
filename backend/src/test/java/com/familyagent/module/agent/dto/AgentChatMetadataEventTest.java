package com.familyagent.module.agent.dto;

import com.familyagent.module.memory.facade.AgentMemoryContextMetadata;
import com.familyagent.module.memory.facade.AgentMemoryRagMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentChatMetadataEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void eventShouldPreserveExistingFlatSseContract() {
        AgentMemoryRagMetadata rag = new AgentMemoryRagMetadata(
                "VECTOR_WITH_TEXT_FALLBACK",
                12,
                1,
                2,
                1,
                0,
                0,
                4,
                List.of());
        AgentChatMetadataEvent event = AgentChatMetadataEvent.create(
                new AgentMemoryContextMetadata(rag, "bedtime health"),
                "chat-request-1",
                91L);

        JsonNode payload = objectMapper.valueToTree(event);

        assertEquals("metadata", payload.path("type").asText());
        assertEquals("chat-request-1", payload.path("requestId").asText());
        assertEquals(91L, payload.path("runId").asLong());
        assertEquals("bedtime health", payload.path("retrievalQuery").asText());
        assertEquals(2, payload.path("rag").path("memoryCount").asInt());
        assertEquals(12, payload.path("rag").path("embeddingReadyCount").asInt());
    }

    @Test
    void eventShouldOmitAbsentOptionalMetadata() {
        JsonNode payload = objectMapper.valueToTree(AgentChatMetadataEvent.create(
                AgentMemoryContextMetadata.empty(),
                "chat-request-2",
                null));

        assertEquals("metadata", payload.path("type").asText());
        assertFalse(payload.has("rag"));
        assertFalse(payload.has("retrievalQuery"));
        assertFalse(payload.has("runId"));
    }
}
