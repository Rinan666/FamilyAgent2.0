package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.agent.harness.constant.AgentConfirmationDecision;
import com.familyagent.module.agent.harness.constant.AgentConfirmationStatus;
import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.dto.AgentToolCallRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.harness.dto.AgentToolConfirmationDecisionResult;
import com.familyagent.module.agent.harness.entity.AgentToolConfirmationRecord;
import com.familyagent.module.agent.harness.repository.AgentToolConfirmationRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolConfirmationDecisionServiceTest {

    private final AgentToolConfirmationRecordRepository repository = mock(AgentToolConfirmationRecordRepository.class);
    private final AgentToolRegistry registry = mock(AgentToolRegistry.class);
    private final AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
    private final AgentRunLifecycleService runLifecycleService = mock(AgentRunLifecycleService.class);
    private final AgentToolConfirmationDecisionService service = new AgentToolConfirmationDecisionService(
            repository,
            registry,
            new AgentToolConfirmationPayloadCodec(new ObjectMapper()),
            toolExecutor,
            runLifecycleService);

    @Test
    void decide_approveRequiredConfirmation_executesApprovedToolOnce() {
        AgentToolConfirmationRecord record = requiredRecord();
        EchoTool tool = new EchoTool();
        when(repository.selectByIdForUpdate(55L)).thenReturn(record);
        doReturn(tool).when(registry).require(EchoTool.NAME);
        when(toolExecutor.executeConfirmed(any(), eq(55L)))
                .thenReturn(AgentToolCallResult.success(new EchoOutput("saved")));

        AgentToolConfirmationDecisionResult result = service.decide(55L, 101L, AgentConfirmationDecision.APPROVE);

        assertEquals(AgentConfirmationStatus.APPROVED.name(), result.confirmation().status());
        assertEquals(AgentToolCallStatus.SUCCEEDED.name(), result.confirmation().executionStatus());
        assertNotNull(result.confirmation().decidedAt());
        assertNotNull(result.confirmation().executedAt());
        assertTrue(result.toolResult().success());

        ArgumentCaptor<AgentToolCallRequest<EchoInput>> requestCaptor = ArgumentCaptor.captor();
        verify(toolExecutor).executeConfirmed(requestCaptor.capture(), eq(55L));
        AgentToolCallRequest<EchoInput> request = requestCaptor.getValue();
        assertEquals(EchoTool.NAME, request.toolName());
        assertEquals("hello", request.input().value());
        assertEquals(10L, request.context().familyId());
        assertEquals(101L, request.context().viewerUserId());
        assertEquals(500L, request.context().runId());
        verify(repository).updateById(record);
    }

    @Test
    void decide_rejectRequiredConfirmation_marksRejected() {
        AgentToolConfirmationRecord record = requiredRecord();
        when(repository.selectByIdForUpdate(55L)).thenReturn(record);

        AgentToolConfirmationDecisionResult result = service.decide(55L, 101L, AgentConfirmationDecision.REJECT);

        assertEquals(AgentConfirmationStatus.REJECTED.name(), result.confirmation().status());
        assertNotNull(result.confirmation().decidedAt());
        verify(toolExecutor, never()).executeConfirmed(any(), any());
        verify(repository).updateById(record);
        verify(runLifecycleService).cancel(500L, "AGENT_TOOL_CONFIRMATION_REJECTED");
    }

    @Test
    void decide_expiredRequiredConfirmation_marksExpired() {
        AgentToolConfirmationRecord record = requiredRecord();
        record.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(repository.selectByIdForUpdate(55L)).thenReturn(record);

        AgentToolConfirmationDecisionResult result = service.decide(55L, 101L, AgentConfirmationDecision.APPROVE);

        assertEquals(AgentConfirmationStatus.EXPIRED.name(), result.confirmation().status());
        assertNotNull(result.confirmation().decidedAt());
        verify(toolExecutor, never()).executeConfirmed(any(), any());
        verify(repository).updateById(record);
        verify(runLifecycleService).fail(500L, "AGENT_TOOL_CONFIRMATION_EXPIRED");
    }

    @Test
    void decide_terminalConfirmation_doesNotExecuteAgain() {
        AgentToolConfirmationRecord record = requiredRecord();
        record.setStatus(AgentConfirmationStatus.APPROVED.name());
        record.setExecutionStatus(AgentToolCallStatus.SUCCEEDED.name());
        when(repository.selectByIdForUpdate(55L)).thenReturn(record);

        AgentToolConfirmationDecisionResult result = service.decide(55L, 101L, AgentConfirmationDecision.APPROVE);

        assertEquals(AgentConfirmationStatus.APPROVED.name(), result.confirmation().status());
        verify(toolExecutor, never()).executeConfirmed(any(), any());
        verify(repository, never()).updateById(record);
    }

    @Test
    void decide_wrongViewer_rejects() {
        AgentToolConfirmationRecord record = requiredRecord();
        when(repository.selectByIdForUpdate(55L)).thenReturn(record);

        assertThrows(BusinessException.class, () -> service.decide(55L, 202L, AgentConfirmationDecision.APPROVE));
        verify(repository, never()).updateById(record);
    }

    private AgentToolConfirmationRecord requiredRecord() {
        AgentToolConfirmationRecord record = new AgentToolConfirmationRecord();
        record.setId(55L);
        record.setRunId(500L);
        record.setToolName(EchoTool.NAME);
        record.setFamilyId(10L);
        record.setViewerUserId(101L);
        record.setRequestId("req-1");
        record.setSessionId(201L);
        record.setAgentMode("family_memory");
        record.setSubject("family");
        record.setContextLabel("test");
        record.setCompleteRunAfterTool(true);
        record.setInputPayload("{\"value\":\"hello\"}");
        record.setStatus(AgentConfirmationStatus.REQUIRED.name());
        record.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return record;
    }

    private record EchoInput(String value) {
    }

    private record EchoOutput(String value) {
    }

    private static class EchoTool implements AgentTool<EchoInput, EchoOutput> {

        private static final String NAME = "echo";
        private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
                NAME,
                "Echo test tool",
                EchoInput.class,
                EchoOutput.class,
                AgentToolSideEffect.WRITE,
                AgentToolConfirmationRequirement.REQUIRED,
                AgentToolPrivacyLevel.FAMILY_PRIVATE);

        @Override
        public AgentToolDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public Class<EchoInput> inputType() {
            return EchoInput.class;
        }

        @Override
        public EchoOutput execute(AgentRunContext context, EchoInput input) {
            return new EchoOutput(input.value());
        }
    }
}
