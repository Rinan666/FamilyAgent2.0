package com.familyagent.module.agent.harness.eval;

import com.familyagent.module.agent.harness.dto.AgentReplayEvent;
import com.familyagent.module.agent.harness.dto.AgentRunReplayArtifact;
import com.familyagent.module.agent.harness.eval.dto.AgentTrajectoryEvalCase;
import com.familyagent.module.agent.harness.eval.dto.AgentTrajectoryEvalReport;
import com.familyagent.module.agent.harness.eval.dto.AgentTrajectoryEvalResult;
import com.familyagent.module.agent.harness.eval.dto.AgentTrajectoryEventExpectation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AgentTrajectoryEvalService {

    private static final String REPORT_SCHEMA_VERSION = "trajectory.eval.report.v1";
    private static final String ERROR_TRAJECTORY_MISMATCH = "TRAJECTORY_MISMATCH";

    public AgentTrajectoryEvalReport evaluate(
            String suiteVersion,
            List<AgentTrajectoryEvalCase> cases) {
        List<AgentTrajectoryEvalCase> safeCases = cases == null ? List.of() : List.copyOf(cases);
        List<AgentTrajectoryEvalResult> results = new ArrayList<>(safeCases.size());
        safeCases.stream().map(this::evaluateCase).forEach(results::add);

        int passed = (int) results.stream().filter(AgentTrajectoryEvalResult::passed).count();
        int failed = results.size() - passed;
        double passRate = results.isEmpty() ? 1.0d : (double) passed / results.size();
        return new AgentTrajectoryEvalReport(
                REPORT_SCHEMA_VERSION,
                suiteVersion,
                new AgentTrajectoryEvalReport.Metrics(results.size(), passed, failed, passRate),
                results);
    }

    private AgentTrajectoryEvalResult evaluateCase(AgentTrajectoryEvalCase evalCase) {
        AgentRunReplayArtifact artifact = evalCase.artifact();
        List<AgentReplayEvent> actual = artifact == null ? List.of() : artifact.trajectory();
        boolean passed = runMatches(evalCase, artifact)
                && actual.size() == evalCase.expectedTrajectory().size()
                && trajectoryMatches(evalCase.expectedTrajectory(), actual);
        return new AgentTrajectoryEvalResult(
                evalCase.caseId(),
                passed,
                evalCase.expectedTrajectory().size(),
                actual.size(),
                passed ? null : ERROR_TRAJECTORY_MISMATCH);
    }

    private boolean runMatches(
            AgentTrajectoryEvalCase evalCase,
            AgentRunReplayArtifact artifact) {
        if (artifact == null || artifact.run() == null) {
            return false;
        }
        return Objects.equals(evalCase.expectedRunStatus(), artifact.run().status())
                && Objects.equals(evalCase.expectedRunErrorCode(), artifact.run().errorCode());
    }

    private boolean trajectoryMatches(
            List<AgentTrajectoryEventExpectation> expected,
            List<AgentReplayEvent> actual) {
        for (int index = 0; index < expected.size(); index++) {
            if (!eventMatches(expected.get(index), actual.get(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean eventMatches(
            AgentTrajectoryEventExpectation expected,
            AgentReplayEvent actual) {
        return expected.eventType() == actual.eventType()
                && Objects.equals(expected.operation(), actual.operation())
                && Objects.equals(expected.status(), actual.status())
                && Objects.equals(expected.errorCode(), actual.errorCode())
                && expected.degraded() == actual.degraded()
                && Objects.equals(expected.inputType(), actual.inputType());
    }
}
