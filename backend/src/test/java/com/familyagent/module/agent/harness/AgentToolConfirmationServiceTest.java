package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentConfirmationStatus;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.entity.AgentToolConfirmationRecord;
import com.familyagent.module.agent.harness.repository.AgentToolConfirmationRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolConfirmationServiceTest {

    private final AgentToolConfirmationRecordRepository repository = mock(AgentToolConfirmationRecordRepository.class);
    private final AgentToolConfirmationService service = new AgentToolConfirmationService(
            repository,
            new AgentToolInputSummarizer(),
            new AgentToolConfirmationPayloadCodec(new ObjectMapper()));

    @Test
    void createRequired_writesPendingConfirmationWithSummaryAndTypedPayload() {
        AgentRunContext context = context();
        AgentToolDescriptor descriptor = descriptor();
        PrivateInput input = new PrivateInput("private child memory");

        AgentToolConfirmationRecord returned = service.createRequired(context, descriptor, input);

        ArgumentCaptor<AgentToolConfirmationRecord> captor = ArgumentCaptor.forClass(AgentToolConfirmationRecord.class);
        verify(repository).insert(captor.capture());
        AgentToolConfirmationRecord record = captor.getValue();
        assertEquals(record, returned);
        assertEquals("create_family_memory", record.getToolName());
        assertEquals(500L, record.getRunId());
        assertEquals(10L, record.getFamilyId());
        assertEquals(101L, record.getViewerUserId());
        assertEquals("request-1", record.getRequestId());
        assertEquals("family_memory", record.getAgentMode());
        assertEquals("family", record.getSubject());
        assertEquals("test", record.getContextLabel());
        assertEquals(true, record.getCompleteRunAfterTool());
        assertEquals("inputType=PrivateInput", record.getInputSummary());
        assertFalse(record.getInputSummary().contains(input.privateText()));
        assertEquals("{\"privateText\":\"private child memory\"}", record.getInputPayload());
        assertEquals(AgentConfirmationStatus.REQUIRED.name(), record.getStatus());
        assertNotNull(record.getIdempotencyKey());
        assertEquals(64, record.getIdempotencyKey().length());
        assertNotNull(record.getExpiresAt());
    }

    @Test
    void createRequired_usesPayloadFingerprintForIdempotency() {
        AgentRunContext context = context();
        AgentToolDescriptor descriptor = descriptor();

        service.createRequired(context, descriptor, new PrivateInput("first memory"));
        service.createRequired(context, descriptor, new PrivateInput("second memory"));

        ArgumentCaptor<AgentToolConfirmationRecord> captor = ArgumentCaptor.forClass(AgentToolConfirmationRecord.class);
        verify(repository, times(2)).insert(captor.capture());
        assertNotEquals(
                captor.getAllValues().get(0).getIdempotencyKey(),
                captor.getAllValues().get(1).getIdempotencyKey());
    }

    @Test
    void createRequired_reusesExistingConfirmationForSamePayload() {
        AgentToolConfirmationRecord existing = new AgentToolConfirmationRecord();
        existing.setId(88L);
        when(repository.selectByIdempotencyKey(anyString())).thenReturn(existing);

        AgentToolConfirmationRecord returned = service.createRequired(
                context(),
                descriptor(),
                new PrivateInput("same memory"));

        assertEquals(existing, returned);
        verify(repository, never()).insert(any());
    }

    private AgentRunContext context() {
        AgentRunContext context = new AgentRunContext(
                500L,
                "request-1",
                10L,
                101L,
                null,
                "family_memory",
                "family",
                "test",
                true);
        return context;
    }

    private AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "create_family_memory",
                "Create family memory",
                PrivateInput.class,
                String.class,
                AgentToolSideEffect.WRITE,
                AgentToolConfirmationRequirement.REQUIRED,
                AgentToolPrivacyLevel.FAMILY_PRIVATE);
    }

    private record PrivateInput(String privateText) {
    }
}
