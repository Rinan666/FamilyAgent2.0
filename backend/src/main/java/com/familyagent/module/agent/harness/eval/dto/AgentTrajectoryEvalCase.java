package com.familyagent.module.agent.harness.eval.dto;

import com.familyagent.module.agent.harness.dto.AgentRunReplayArtifact;

import java.util.List;

public record AgentTrajectoryEvalCase(
        String caseId,
        String expectedRunStatus,
        String expectedRunErrorCode,
        List<AgentTrajectoryEventExpectation> expectedTrajectory,
        AgentRunReplayArtifact artifact) {

    public AgentTrajectoryEvalCase {
        expectedTrajectory = expectedTrajectory == null
                ? List.of()
                : List.copyOf(expectedTrajectory);
    }
}
