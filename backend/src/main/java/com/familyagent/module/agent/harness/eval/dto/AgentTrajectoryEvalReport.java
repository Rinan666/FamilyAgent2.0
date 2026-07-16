package com.familyagent.module.agent.harness.eval.dto;

import java.util.List;

public record AgentTrajectoryEvalReport(
        String schemaVersion,
        String suiteVersion,
        Metrics metrics,
        List<AgentTrajectoryEvalResult> results) {

    public AgentTrajectoryEvalReport {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public record Metrics(
            int caseCount,
            int passedCount,
            int failedCount,
            double trajectoryPassRate) {
    }
}
