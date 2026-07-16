package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.dto.AgentRunTrace;
import com.familyagent.module.agent.harness.entity.AgentRunRecord;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;
import com.familyagent.module.agent.harness.repository.AgentRunStepRecordRepository;
import com.familyagent.module.agent.harness.repository.AgentToolCallRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunTraceQueryServiceTest {

    private final AgentRunQueryService runQueryService = mock(AgentRunQueryService.class);
    private final AgentRunStepRecordRepository stepRepository = mock(AgentRunStepRecordRepository.class);
    private final AgentToolCallRecordRepository toolCallRepository = mock(AgentToolCallRecordRepository.class);
    private final AgentRunTraceQueryService service = new AgentRunTraceQueryService(
            runQueryService,
            stepRepository,
            toolCallRepository);

    @Test
    void getBuildsOrderedToolTraceForRun() {
        AgentRunRecord run = new AgentRunRecord();
        run.setId(91L);
        AgentToolCallRecord call = new AgentToolCallRecord();
        call.setRunId(91L);
        AgentRunStepRecord step = new AgentRunStepRecord();
        step.setRunId(91L);
        when(runQueryService.get(91L)).thenReturn(run);
        when(stepRepository.findByRunId(91L)).thenReturn(List.of(step));
        when(toolCallRepository.findByRunId(91L)).thenReturn(List.of(call));

        AgentRunTrace trace = service.get(91L);

        assertEquals(run, trace.run());
        assertEquals(List.of(step), trace.steps());
        assertEquals(List.of(call), trace.toolCalls());
    }
}
