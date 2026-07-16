package com.familyagent.module.agent.harness.eval.dto;

public record AgentTrajectoryEvalResult(
        String caseId,
        boolean passed,
        int expectedEventCount,
        int actualEventCount,
        String errorCode) {
}
