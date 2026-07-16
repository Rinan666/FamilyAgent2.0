package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;
import com.familyagent.module.agent.harness.repository.AgentToolCallRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentToolAuditServiceTest {

    private final AgentToolCallRecordRepository repository = mock(AgentToolCallRecordRepository.class);
    private final AgentToolAuditService auditService = new AgentToolAuditService(
            repository,
            new AgentToolInputSummarizer());

    @Test
    void record_writesMinimalNonRawInputSummary() {
        AgentRunContext context = new AgentRunContext(
                500L,
                "request-with-a-long-but-valid-id",
                10L,
                101L,
                null,
                "family_memory",
                "family",
                "test",
                true);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "recall_family_memory",
                "Recall memory",
                PrivateInput.class,
                String.class,
                AgentToolSideEffect.READ_ONLY,
                AgentToolConfirmationRequirement.NOT_REQUIRED,
                AgentToolPrivacyLevel.FAMILY_PRIVATE);
        PrivateInput input = new PrivateInput("child said a private sentence");

        auditService.record(context, descriptor, input, AgentToolCallStatus.SUCCEEDED, null);

        ArgumentCaptor<AgentToolCallRecord> captor = ArgumentCaptor.forClass(AgentToolCallRecord.class);
        verify(repository).insert(captor.capture());
        AgentToolCallRecord record = captor.getValue();
        assertEquals("recall_family_memory", record.getToolName());
        assertEquals(500L, record.getRunId());
        assertEquals(10L, record.getFamilyId());
        assertEquals(101L, record.getViewerUserId());
        assertEquals("inputType=PrivateInput", record.getInputSummary());
        assertFalse(record.getInputSummary().contains(input.privateText()));
        assertEquals(AgentToolCallStatus.SUCCEEDED.name(), record.getStatus());
    }

    private record PrivateInput(String privateText) {
    }
}
