package com.familyagent.module.agent.harness.provenance;

import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentToolDescriptor;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.dto.CreateFamilyMemoryOutput;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;
import com.familyagent.module.memory.facade.UnifiedMemoryIdentityFacade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AgentRecordProvenanceWriterTest {

    private final AgentRecordProvenanceRepository repository = mock(AgentRecordProvenanceRepository.class);
    private final UnifiedMemoryIdentityFacade memoryIdentityFacade = mock(UnifiedMemoryIdentityFacade.class);
    private final AgentRecordProvenanceWriter writer = new AgentRecordProvenanceWriter(
            repository,
            memoryIdentityFacade);

    @Test
    void recordCreatedOutputLinksFinalRecordToRunAndToolCall() {
        AgentRunContext context = new AgentRunContext(
                501L, "req-1", 10L, 101L, null, "family_memory", "family", "test", true);
        AgentToolDescriptor descriptor = descriptor();
        AgentToolCallRecord toolCall = new AgentToolCallRecord();
        toolCall.setId(701L);

        writer.recordCreatedOutput(context, descriptor, toolCall, new CreateFamilyMemoryOutput(901L));

        ArgumentCaptor<AgentRecordProvenance> captor = ArgumentCaptor.forClass(AgentRecordProvenance.class);
        verify(repository).insert(captor.capture());
        AgentRecordProvenance record = captor.getValue();
        assertEquals(10L, record.getFamilyId());
        assertEquals(501L, record.getAgentRunId());
        assertEquals(701L, record.getToolCallId());
        assertEquals("create_family_memory", record.getToolName());
        assertEquals(AgentToolDescriptor.DEFAULT_VERSION, record.getToolVersion());
        assertEquals(AgentCreatedRecordType.MEMORY_ENTRY.name(), record.getRecordType());
        assertEquals(901L, record.getMemoryEntryId());
    }

    @Test
    void recordCreatedOutputDoesNotMislabelManualOrReadOnlyOutput() {
        writer.recordCreatedOutput(null, descriptor(), new AgentToolCallRecord(), new CreateFamilyMemoryOutput(901L));
        writer.recordCreatedOutput(
                new AgentRunContext("req", 10L, 101L, null, "mode", "subject", "test"),
                descriptor(),
                new AgentToolCallRecord(),
                "read-only result");

        verify(repository, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    private static AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "create_family_memory",
                "Create memory",
                Object.class,
                CreateFamilyMemoryOutput.class,
                AgentToolSideEffect.WRITE,
                AgentToolConfirmationRequirement.REQUIRED,
                AgentToolPrivacyLevel.FAMILY_PRIVATE);
    }
}
