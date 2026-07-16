package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.agent.harness.entity.AgentRunRecord;
import com.familyagent.module.agent.harness.repository.AgentRunRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunQueryServiceTest {

    private final AgentRunRecordRepository repository = mock(AgentRunRecordRepository.class);
    private final AgentRunQueryService service = new AgentRunQueryService(repository);

    @Test
    void getReturnsRunById() {
        AgentRunRecord run = new AgentRunRecord();
        run.setId(91L);
        when(repository.selectById(91L)).thenReturn(run);

        assertEquals(run, service.get(91L));
    }

    @Test
    void getRejectsUnknownRun() {
        assertThrows(BusinessException.class, () -> service.get(91L));
    }

    @Test
    void listByRequestIdDelegatesToRepositoryBoundary() {
        AgentRunRecord run = new AgentRunRecord();
        when(repository.findByRequestId("request-1")).thenReturn(List.of(run));

        assertEquals(List.of(run), service.listByRequestId(" request-1 "));
        verify(repository).findByRequestId("request-1");
    }
}
