package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.harness.entity.AgentRunRecord;
import com.familyagent.module.agent.harness.repository.AgentRunRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentRunQueryService {

    private final AgentRunRecordRepository repository;

    public AgentRunRecord get(Long runId) {
        AgentRunRecord run = runId == null ? null : repository.selectById(runId);
        if (run == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent run not found");
        }
        return run;
    }

    public List<AgentRunRecord> listByRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return List.of();
        }
        return repository.findByRequestId(requestId.trim());
    }

    public List<AgentRunRecord> listBySessionId(Long sessionId) {
        return sessionId == null ? List.of() : repository.findBySessionId(sessionId);
    }
}
