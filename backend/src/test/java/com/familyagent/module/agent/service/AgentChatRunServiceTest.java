package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.dto.AgentChatStreamRequest;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentRunLifecycleService;
import com.familyagent.module.agent.harness.AgentTraceRecorder;
import com.familyagent.module.agent.harness.constant.AgentRunStepType;
import com.familyagent.module.agent.harness.dto.AgentTraceObservation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatRunServiceTest {

    private final AgentRunLifecycleService lifecycleService = mock(AgentRunLifecycleService.class);
    private final AgentTraceRecorder traceRecorder = mock(AgentTraceRecorder.class);
    private final AgentChatRunService service = new AgentChatRunService(lifecycleService, traceRecorder);

    @Test
    void startCreatesParentRunContextThatChildToolsCannotComplete() {
        AgentChatStreamRequest request = new AgentChatStreamRequest();
        request.setFamilyId(10L);
        request.setKnowledgePoint("family_memory");
        request.setSubject("FamilyAgent");
        when(lifecycleService.startOrResume(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> ((AgentRunContext) invocation.getArgument(0)).withRunId(91L));

        AgentRunContext context = service.start(request, 101L, "request-1");

        assertEquals(91L, context.runId());
        assertEquals("request-1", context.requestId());
        assertFalse(context.completeRunAfterTool());
    }

    @Test
    void completePreservesUpstreamErrorCode() {
        AgentRunContext context = context();
        AgentChatStreamTracker tracker = mock(AgentChatStreamTracker.class);
        when(tracker.failed()).thenReturn(true);
        when(tracker.errorCode()).thenReturn("AI_PROVIDER_UNAVAILABLE");

        service.complete(context, tracker);

        verify(lifecycleService).fail(context, "AI_PROVIDER_UNAVAILABLE");
    }

    @Test
    void completeMarksDoneStreamSucceeded() {
        AgentRunContext context = context();
        AgentChatStreamTracker tracker = mock(AgentChatStreamTracker.class);
        when(tracker.completedSuccessfully()).thenReturn(true);

        service.complete(context, tracker);

        verify(lifecycleService).succeed(context);
    }

    @Test
    void completeRecordsTerminalObservationsBeforeFinalizingRun() {
        AgentRunContext context = context();
        AgentChatStreamTracker tracker = mock(AgentChatStreamTracker.class);
        AgentTraceObservation observation = new AgentTraceObservation(
                AgentRunStepType.LLM,
                "llm.chat_stream",
                "dashscope",
                "dashscope/qwen-flash",
                null,
                null,
                12L,
                true,
                null,
                false,
                List.of());
        when(tracker.traceObservations()).thenReturn(List.of(observation));
        when(tracker.completedSuccessfully()).thenReturn(true);

        service.complete(context, tracker);

        var ordered = inOrder(traceRecorder, lifecycleService);
        ordered.verify(traceRecorder).recordObservation(context, observation);
        ordered.verify(lifecycleService).succeed(context);
    }

    @Test
    void failPreparationMapsRateLimit() {
        AgentRunContext context = context();

        service.failPreparation(
                context,
                new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "rate limited"));

        verify(lifecycleService).fail(context, "AGENT_RUN_RATE_LIMITED");
    }

    private AgentRunContext context() {
        return new AgentRunContext(
                91L,
                "request-1",
                10L,
                101L,
                null,
                "family_memory",
                "FamilyAgent",
                "chat_stream",
                false);
    }
}
