package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.agent.dto.AgentRunView;
import com.familyagent.module.agent.harness.AgentRunTraceQueryService;
import com.familyagent.module.agent.harness.AgentRunQueryService;
import com.familyagent.module.agent.harness.dto.AgentRunTrace;
import com.familyagent.module.agent.harness.entity.AgentRunRecord;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunViewServiceTest {

    private final AgentRunTraceQueryService traceQueryService = mock(AgentRunTraceQueryService.class);
    private final AgentRunQueryService runQueryService = mock(AgentRunQueryService.class);
    private final AgentRunViewService service = new AgentRunViewService(traceQueryService, runQueryService);

    @Test
    void getReturnsOwnedRunWithOrderedSafeEvents() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 11, 20, 10, 0);
        AgentRunRecord run = run(91L, 7L, "FAILED", startedAt);
        AgentRunStepRecord step = new AgentRunStepRecord();
        step.setId(12L);
        step.setStepType("LLM");
        step.setOperation("llm.chat_stream");
        step.setStatus("FAILED");
        step.setErrorCode("AI_TIMEOUT");
        step.setStartedAt(startedAt.plusSeconds(2));
        AgentToolCallRecord tool = new AgentToolCallRecord();
        tool.setId(11L);
        tool.setToolName("recall_family_memory");
        tool.setStatus("SUCCEEDED");
        tool.setInputSummary("private input must not be exposed");
        tool.setCreatedAt(startedAt.plusSeconds(1));
        when(traceQueryService.get(91L)).thenReturn(new AgentRunTrace(run, List.of(step), List.of(tool)));

        AgentRunView result = service.get(91L, 7L);

        assertTrue(result.retryable());
        assertEquals("recall_family_memory", result.events().get(0).operation());
        assertEquals("llm.chat_stream", result.events().get(1).operation());
    }

    @Test
    void getRejectsRunOwnedByAnotherUser() {
        AgentRunRecord run = run(91L, 7L, "SUCCEEDED", LocalDateTime.now());
        when(traceQueryService.get(91L)).thenReturn(new AgentRunTrace(run, List.of(), List.of()));

        assertThrows(BusinessException.class, () -> service.get(91L, 8L));
    }

    @Test
    void listBySessionReturnsOnlyRunsOwnedByViewer() {
        AgentRunRecord owned = run(91L, 7L, "SUCCEEDED", LocalDateTime.now());
        AgentRunRecord foreign = run(92L, 8L, "FAILED", LocalDateTime.now());
        when(runQueryService.listBySessionId(33L)).thenReturn(List.of(owned, foreign));

        var result = service.listBySession(33L, 7L);

        assertEquals(List.of(91L), result.stream().map(view -> view.runId()).toList());
    }

    private static AgentRunRecord run(Long id, Long viewerUserId, String status, LocalDateTime startedAt) {
        AgentRunRecord run = new AgentRunRecord();
        run.setId(id);
        run.setViewerUserId(viewerUserId);
        run.setSessionId(33L);
        run.setContextLabel("family_chat");
        run.setStatus(status);
        run.setStartedAt(startedAt);
        return run;
    }
}
