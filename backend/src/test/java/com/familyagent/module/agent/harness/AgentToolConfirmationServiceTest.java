package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentConfirmationStatus;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.entity.AgentToolConfirmationRecord;
import com.familyagent.module.agent.harness.repository.AgentToolConfirmationRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentToolConfirmationServiceTest {

    private final AgentToolConfirmationRecordRepository repository = mock(AgentToolConfirmationRecordRepository.class);
    private final AgentToolConfirmationService service = new AgentToolConfirmationService(
            repository,
            new AgentToolInputSummarizer());

    @Test
    void createRequired_writesPendingConfirmationWithoutRawInput() {
        AgentRunContext context = new AgentRunContext(
                "request-1",
                10L,
                101L,
                null,
                "family_memory",
                "family",
                "test");
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "create_family_memory",
                "Create family memory",
                PrivateInput.class,
                String.class,
                AgentToolSideEffect.WRITE,
                AgentToolConfirmationRequirement.REQUIRED,
                AgentToolPrivacyLevel.FAMILY_PRIVATE);
        PrivateInput input = new PrivateInput("private child memory");

        AgentToolConfirmationRecord returned = service.createRequired(context, descriptor, input);

        ArgumentCaptor<AgentToolConfirmationRecord> captor = ArgumentCaptor.forClass(AgentToolConfirmationRecord.class);
        verify(repository).insert(captor.capture());
        AgentToolConfirmationRecord record = captor.getValue();
        assertEquals(record, returned);
        assertEquals("create_family_memory", record.getToolName());
        assertEquals(10L, record.getFamilyId());
        assertEquals(101L, record.getViewerUserId());
        assertEquals("request-1", record.getRequestId());
        assertEquals("inputType=PrivateInput", record.getInputSummary());
        assertFalse(record.getInputSummary().contains(input.privateText()));
        assertEquals(AgentConfirmationStatus.REQUIRED.name(), record.getStatus());
        assertNotNull(record.getIdempotencyKey());
        assertEquals(64, record.getIdempotencyKey().length());
        assertNotNull(record.getExpiresAt());
    }

    private record PrivateInput(String privateText) {
    }
}
