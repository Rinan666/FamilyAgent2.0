package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.agent.harness.entity.AgentRunRecord;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;

import java.util.List;

public record AgentRunTrace(
        AgentRunRecord run,
        List<AgentRunStepRecord> steps,
        List<AgentToolCallRecord> toolCalls
) {
    public AgentRunTrace {
        steps = steps == null ? List.of() : List.copyOf(steps);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
