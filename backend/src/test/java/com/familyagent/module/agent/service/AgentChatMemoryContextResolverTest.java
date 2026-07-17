package com.familyagent.module.agent.service;

import com.familyagent.module.agent.dto.AgentChatStreamRequest;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.AgentToolExecutor;
import com.familyagent.module.agent.harness.dto.AgentToolCallRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.harness.dto.RecallFamilyMemoryInput;
import com.familyagent.module.agent.harness.dto.RecallFamilyMemoryOutput;
import com.familyagent.module.family.facade.AgentPersonaContextFacade;
import com.familyagent.module.mirror.facade.AgentMirrorContextFacade;
import com.familyagent.module.memory.facade.AgentMemoryContextMetadata;
import com.familyagent.module.memory.facade.AgentMemoryRagMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatMemoryContextResolverTest {

    private final AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
    private final AgentMirrorContextFacade mirrorContextFacade = mock(AgentMirrorContextFacade.class);
    private final AgentPersonaContextFacade personaContextFacade = mock(AgentPersonaContextFacade.class);
    private final AgentChatMemoryContextResolver resolver = new AgentChatMemoryContextResolver(
            toolExecutor,
            mirrorContextFacade,
            personaContextFacade);

    @Test
    void resolve_familyMemoryContext_usesRecallToolExecutor() {
        AgentChatStreamRequest request = familyMemoryRequest();
        request.setMemoryContext("client supplied context");
        AgentChatStreamRequest.HistoryMessage userHistory = new AgentChatStreamRequest.HistoryMessage();
        userHistory.setRole("user");
        userHistory.setContent("earlier user turn");
        AgentChatStreamRequest.HistoryMessage assistantHistory = new AgentChatStreamRequest.HistoryMessage();
        assistantHistory.setRole("assistant");
        assistantHistory.setContent("assistant turn");
        request.setHistory(List.of(userHistory, assistantHistory));
        when(toolExecutor.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(AgentToolCallResult.success(new RecallFamilyMemoryOutput("authorized context")));

        AgentChatMemoryResolution resolution = resolver.resolve(request, runContext());

        assertEquals("authorized context", resolution.context());
        ArgumentCaptor<AgentToolCallRequest<RecallFamilyMemoryInput>> captor = ArgumentCaptor.forClass(AgentToolCallRequest.class);
        verify(toolExecutor).execute(captor.capture());
        AgentToolCallRequest<RecallFamilyMemoryInput> call = captor.getValue();
        AgentRunContext runContext = call.context();
        assertEquals(AgentToolName.RECALL_FAMILY_MEMORY.value(), call.toolName());
        assertEquals(10L, runContext.familyId());
        assertEquals(101L, runContext.viewerUserId());
        assertEquals("req-1", runContext.requestId());
        assertEquals(500L, runContext.runId());
        assertFalse(runContext.completeRunAfterTool());
        assertEquals("How should I talk about bedtime?", call.input().memberMessage());
        assertEquals(List.of("earlier user turn"), call.input().recentUserMessages());
    }

    @Test
    void resolve_familyMemoryToolFailure_returnsEmptyContext() {
        AgentChatStreamRequest request = familyMemoryRequest();
        when(toolExecutor.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(AgentToolCallResult.failure(
                        com.familyagent.module.agent.harness.constant.AgentToolCallStatus.DENIED,
                        "AGENT_TOOL_PERMISSION_DENIED",
                        "denied",
                        false));

        AgentChatMemoryResolution resolution = resolver.resolve(request, runContext());

        assertEquals("", resolution.context());
        assertTrue(resolution.metadata().isEmpty());
    }

    @Test
    void resolve_quickMode_usesClientContextForBackwardCompatibility() {
        AgentChatStreamRequest request = familyMemoryRequest();
        request.setResponseMode("quick");
        request.setMemoryContext("client context");

        AgentChatMemoryResolution resolution = resolver.resolve(request, runContext());

        assertEquals("client context", resolution.context());
        verify(toolExecutor, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolve_personaThinkMode_appendsFamilyReferenceFromTool() {
        AgentChatStreamRequest request = familyMemoryRequest();
        request.setKnowledgePoint("persona_member");
        request.setTargetPersonaId(202L);
        when(personaContextFacade.buildPersonaAgentContext(10L, 202L))
                .thenReturn("persona context");
        when(toolExecutor.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(AgentToolCallResult.success(new RecallFamilyMemoryOutput(
                        "family context",
                        new AgentMemoryContextMetadata(
                                new AgentMemoryRagMetadata(null, 0, 0, 1, 0, 0, 0, 1, List.of()),
                                null))));

        AgentChatMemoryResolution resolution = resolver.resolve(request, runContext());

        assertTrue(resolution.context().contains("persona context"));
        assertTrue(resolution.context().contains("family_visible_reference"));
        assertTrue(resolution.context().contains("family context"));
        assertEquals(1, resolution.metadata().rag().memoryCount());
    }

    private AgentChatStreamRequest familyMemoryRequest() {
        AgentChatStreamRequest request = new AgentChatStreamRequest();
        request.setMemberMessage("How should I talk about bedtime?");
        request.setFamilyId(10L);
        request.setKnowledgePoint("family_memory");
        request.setSubject("FamilyAgent");
        request.setResponseMode("think");
        return request;
    }

    private AgentRunContext runContext() {
        return new AgentRunContext(
                500L,
                "req-1",
                10L,
                101L,
                null,
                "family_memory",
                "FamilyAgent",
                "chat_stream",
                false);
    }
}
