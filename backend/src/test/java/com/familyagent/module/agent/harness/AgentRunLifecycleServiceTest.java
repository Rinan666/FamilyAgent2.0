package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentRunStatus;
import com.familyagent.module.agent.harness.entity.AgentRunRecord;
import com.familyagent.module.agent.harness.repository.AgentRunRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentRunLifecycleServiceTest {

    private final AgentRunRecordRepository repository = mock(AgentRunRecordRepository.class);
    private final AgentRunLifecycleService service = new AgentRunLifecycleService(repository);

    @Test
    void startOrResumeCreatesPrivacySafeRunRecord() {
        doAnswer(invocation -> {
            AgentRunRecord record = invocation.getArgument(0);
            record.setId(91L);
            return 1;
        }).when(repository).insert(any(AgentRunRecord.class));
        AgentRunContext context = new AgentRunContext(
                "request-1",
                10L,
                101L,
                201L,
                "family",
                "FamilyAgent",
                "save_memory");

        AgentRunContext started = service.startOrResume(context);

        assertEquals(91L, started.runId());
        ArgumentCaptor<AgentRunRecord> captor = ArgumentCaptor.forClass(AgentRunRecord.class);
        verify(repository).insert(captor.capture());
        AgentRunRecord record = captor.getValue();
        assertEquals("request-1", record.getRequestId());
        assertEquals(10L, record.getFamilyId());
        assertEquals(101L, record.getViewerUserId());
        assertEquals(AgentRunStatus.RUNNING.name(), record.getStatus());
        assertNotNull(record.getStartedAt());
        assertNull(record.getErrorCode());
    }

    @Test
    void failWritesTerminalStatusAndSafeErrorCode() {
        AgentRunContext context = new AgentRunContext(
                91L,
                "request-1",
                10L,
                101L,
                null,
                "family",
                "FamilyAgent",
                "save_memory",
                true);

        service.fail(context, "AGENT_TOOL_EXECUTION_FAILED");

        ArgumentCaptor<AgentRunRecord> captor = ArgumentCaptor.forClass(AgentRunRecord.class);
        verify(repository).updateById(captor.capture());
        AgentRunRecord update = captor.getValue();
        assertEquals(91L, update.getId());
        assertEquals(AgentRunStatus.FAILED.name(), update.getStatus());
        assertEquals("AGENT_TOOL_EXECUTION_FAILED", update.getErrorCode());
        assertNotNull(update.getCompletedAt());
    }
}
