package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.dto.AgentRunTrace;
import com.familyagent.module.agent.harness.entity.AgentRunRecord;
import com.familyagent.module.agent.harness.repository.AgentRunStepRecordRepository;
import com.familyagent.module.agent.harness.repository.AgentToolCallRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentRunTraceQueryService {

    private final AgentRunQueryService runQueryService;
    private final AgentRunStepRecordRepository stepRepository;
    private final AgentToolCallRecordRepository toolCallRepository;

    public AgentRunTrace get(Long runId) {
        AgentRunRecord run = runQueryService.get(runId);
        return new AgentRunTrace(
                run,
                stepRepository.findByRunId(runId),
                toolCallRepository.findByRunId(runId));
    }
}
