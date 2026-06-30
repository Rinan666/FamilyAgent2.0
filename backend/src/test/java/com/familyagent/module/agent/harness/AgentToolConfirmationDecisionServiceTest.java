package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.agent.harness.constant.AgentConfirmationDecision;
import com.familyagent.module.agent.harness.constant.AgentConfirmationStatus;
import com.familyagent.module.agent.harness.entity.AgentToolConfirmationRecord;
import com.familyagent.module.agent.harness.repository.AgentToolConfirmationRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolConfirmationDecisionServiceTest {

    private final AgentToolConfirmationRecordRepository repository = mock(AgentToolConfirmationRecordRepository.class);
    private final AgentToolConfirmationService service = new AgentToolConfirmationService(
            repository,
            new AgentToolInputSummarizer());

    @Test
    void decide_approveRequiredConfirmation_marksApproved() {
        AgentToolConfirmationRecord record = requiredRecord();
        when(repository.selectById(55L)).thenReturn(record);

        AgentToolConfirmationRecord result = service.decide(55L, 101L, AgentConfirmationDecision.APPROVE);

        assertEquals(AgentConfirmationStatus.APPROVED.name(), result.getStatus());
        assertNotNull(result.getDecidedAt());
        verify(repository).updateById(record);
    }

    @Test
    void decide_rejectRequiredConfirmation_marksRejected() {
        AgentToolConfirmationRecord record = requiredRecord();
        when(repository.selectById(55L)).thenReturn(record);

        AgentToolConfirmationRecord result = service.decide(55L, 101L, AgentConfirmationDecision.REJECT);

        assertEquals(AgentConfirmationStatus.REJECTED.name(), result.getStatus());
        assertNotNull(result.getDecidedAt());
        verify(repository).updateById(record);
    }

    @Test
    void decide_expiredRequiredConfirmation_marksExpired() {
        AgentToolConfirmationRecord record = requiredRecord();
        record.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(repository.selectById(55L)).thenReturn(record);

        AgentToolConfirmationRecord result = service.decide(55L, 101L, AgentConfirmationDecision.APPROVE);

        assertEquals(AgentConfirmationStatus.EXPIRED.name(), result.getStatus());
        assertNotNull(result.getDecidedAt());
        verify(repository).updateById(record);
    }

    @Test
    void decide_terminalConfirmation_isIdempotent() {
        AgentToolConfirmationRecord record = requiredRecord();
        record.setStatus(AgentConfirmationStatus.APPROVED.name());
        when(repository.selectById(55L)).thenReturn(record);

        AgentToolConfirmationRecord result = service.decide(55L, 101L, AgentConfirmationDecision.APPROVE);

        assertEquals(AgentConfirmationStatus.APPROVED.name(), result.getStatus());
        verify(repository, never()).updateById(record);
    }

    @Test
    void decide_wrongViewer_rejects() {
        AgentToolConfirmationRecord record = requiredRecord();
        when(repository.selectById(55L)).thenReturn(record);

        assertThrows(BusinessException.class, () -> service.decide(55L, 202L, AgentConfirmationDecision.APPROVE));
        verify(repository, never()).updateById(record);
    }

    private AgentToolConfirmationRecord requiredRecord() {
        AgentToolConfirmationRecord record = new AgentToolConfirmationRecord();
        record.setId(55L);
        record.setViewerUserId(101L);
        record.setStatus(AgentConfirmationStatus.REQUIRED.name());
        record.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return record;
    }
}
