package com.familyagent.infra.ai;

import com.familyagent.infra.ai.dto.OrganizeDraftPayload;
import com.familyagent.infra.ai.dto.OrganizeDraftResponse;
import com.familyagent.infra.ai.dto.PersonaMaterialDraftResponse;
import com.familyagent.module.agent.dto.AgentOrganizedDraft;
import com.familyagent.module.agent.dto.AgentPersonaMaterialDraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DraftGenerationClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final DraftGenerationClient client = new DraftGenerationClient(
            restTemplate,
            "http://ai-service",
            "internal-token",
            meterRegistry);

    @Test
    void organizeSendsInternalIdentityAndRunHeaders() {
        AgentOrganizedDraft draft = new AgentOrganizedDraft();
        draft.setTitle("A draft");
        OrganizeDraftResponse response = new OrganizeDraftResponse();
        response.setSuccess(true);
        response.setData(draft);
        when(restTemplate.postForEntity(
                eq("http://ai-service/ai/memory/organize-draft"),
                any(HttpEntity.class),
                eq(OrganizeDraftResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        OrganizeDraftResponse result = client.organize(
                new OrganizeDraftPayload("content", "DIARY", "context", "DAILY", "PRIVATE", ""),
                "draft-request",
                91L);

        assertEquals(response, result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<OrganizeDraftPayload>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("http://ai-service/ai/memory/organize-draft"),
                captor.capture(),
                eq(OrganizeDraftResponse.class));
        assertEquals("internal-token", captor.getValue().getHeaders().getFirst("X-Internal-Service-Token"));
        assertEquals("draft-request", captor.getValue().getHeaders().getFirst("X-Request-Id"));
        assertEquals("91", captor.getValue().getHeaders().getFirst("X-Agent-Run-Id"));
        assertEquals(1, meterRegistry.find("familyagent.ai.client.request")
                .tag("operation", "organize_draft")
                .tag("success", "true")
                .timer()
                .count());
    }

    @Test
    void inputRejectionRemainsStructuredAndSafe() {
        HttpClientErrorException error = HttpClientErrorException.create(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Unprocessable Entity",
                HttpHeaders.EMPTY,
                new byte[0],
                null);
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(OrganizeDraftResponse.class)))
                .thenThrow(error);

        OrganizeDraftResponse response = client.organize(
                new OrganizeDraftPayload("content", "DIARY", "", "", "", ""),
                "draft-request",
                91L);

        assertFalse(response.isSuccess());
        assertEquals("AI_INPUT_REJECTED", response.getErrorCode());
    }

    @Test
    void snakeCaseAiResponseIsMappedToCamelCaseBackendDto() throws Exception {
        String json = """
                {
                  "success": true,
                  "data": {
                    "profile": {
                      "name": "Ada",
                      "description": "",
                      "era_identity": "Victorian era",
                      "values": "Curiosity",
                      "speaking_style": "Precise",
                      "personality": "Thoughtful"
                    },
                    "materials": [{"title":"Note","content":"Long enough","tags":["history"]}],
                    "reason": "Structured"
                  }
                }
                """;

        PersonaMaterialDraftResponse response = new ObjectMapper().readValue(
                json,
                PersonaMaterialDraftResponse.class);
        AgentPersonaMaterialDraft.Profile profile = response.getData().getProfile();

        assertEquals("Victorian era", profile.getEraIdentity());
        assertEquals("Precise", profile.getSpeakingStyle());
        assertEquals(List.of("history"), response.getData().getMaterials().get(0).getTags());
    }
}
