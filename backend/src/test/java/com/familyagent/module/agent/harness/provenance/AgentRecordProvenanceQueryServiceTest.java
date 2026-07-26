package com.familyagent.module.agent.harness.provenance;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.facade.UnifiedMemoryIdentityFacade;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRecordProvenanceQueryServiceTest {

    private final AgentRecordProvenanceRepository repository = mock(AgentRecordProvenanceRepository.class);
    private final UnifiedMemoryIdentityFacade memoryIdentityFacade = mock(UnifiedMemoryIdentityFacade.class);
    private final AgentRecordProvenanceQueryService service = new AgentRecordProvenanceQueryService(
            repository,
            memoryIdentityFacade);

    @Test
    void findReturnsStableInternalViewWithoutPromptOrModelOutput() {
        AgentRecordProvenance record = new AgentRecordProvenance();
        record.setAgentRunId(501L);
        record.setToolCallId(701L);
        record.setToolName("create_diary_entry");
        record.setToolVersion("1.0.0");
        record.setCreatedAt(LocalDateTime.of(2026, 7, 17, 10, 0));
        when(memoryIdentityFacade.findMemoryEntryId(MemoryOriginType.DIARY, 901L)).thenReturn(1901L);
        when(repository.findByRecord(AgentCreatedRecordType.DIARY_ENTRY, 1901L)).thenReturn(record);

        AgentRecordProvenanceView view = service.find(AgentCreatedRecordType.DIARY_ENTRY, 901L).orElseThrow();

        assertEquals(AgentCreatedRecordType.DIARY_ENTRY, view.recordType());
        assertEquals(901L, view.recordId());
        assertEquals(501L, view.agentRunId());
        assertEquals(701L, view.toolCallId());
        assertEquals("create_diary_entry", view.toolName());
    }

    @Test
    void findReturnsEmptyForManualRecord() {
        assertTrue(service.find(AgentCreatedRecordType.MEMORY_ENTRY, 999L).isEmpty());
    }
}
