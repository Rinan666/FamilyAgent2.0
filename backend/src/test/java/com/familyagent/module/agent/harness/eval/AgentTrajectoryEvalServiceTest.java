package com.familyagent.module.agent.harness.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.module.agent.harness.constant.AgentReplayEventType;
import com.familyagent.module.agent.harness.constant.AgentRunStatus;
import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.constant.AgentToolErrorCode;
import com.familyagent.module.agent.harness.dto.AgentReplayEvent;
import com.familyagent.module.agent.harness.dto.AgentRunReplayArtifact;
import com.familyagent.module.agent.harness.eval.dto.AgentTrajectoryEvalCase;
import com.familyagent.module.agent.harness.eval.dto.AgentTrajectoryEvalReport;
import com.familyagent.module.agent.harness.eval.dto.AgentTrajectoryEventExpectation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentTrajectoryEvalServiceTest {

    private static final String SUITE_VERSION = "agent.trajectory.core.v1";
    private static final String TOOL_NAME = "create_family_memory";
    private static final String INPUT_TYPE = "inputType=CreateFamilyMemoryInput";

    private final AgentTrajectoryEvalService service = new AgentTrajectoryEvalService();

    @Test
    void evaluatePassesPermissionAndConfirmationGoldenFixtures() throws Exception {
        AgentTrajectoryEvalReport report = service.evaluate(SUITE_VERSION, List.of(
                permissionDeniedCase(),
                confirmationRejectedCase(),
                duplicatedConfirmationCase()));

        assertEquals("trajectory.eval.report.v1", report.schemaVersion());
        assertEquals(SUITE_VERSION, report.suiteVersion());
        assertEquals(3, report.metrics().caseCount());
        assertEquals(3, report.metrics().passedCount());
        assertEquals(0, report.metrics().failedCount());
        assertEquals(1.0d, report.metrics().trajectoryPassRate());

        String json = new ObjectMapper().writeValueAsString(report);
        assertFalse(json.contains("private-request-reference"));
        assertFalse(json.contains("private family content"));
        assertFalse(json.contains("runId"));
        assertFalse(json.contains("requestRef"));
        assertFalse(json.contains("inputType"));
        assertFalse(json.contains(TOOL_NAME));
    }

    @Test
    void evaluateReturnsStableMismatchWithoutArtifactDetails() {
        AgentTrajectoryEvalCase evalCase = new AgentTrajectoryEvalCase(
                "permission-denied",
                AgentRunStatus.SUCCEEDED.name(),
                null,
                List.of(),
                permissionDeniedCase().artifact());

        AgentTrajectoryEvalReport report = service.evaluate(SUITE_VERSION, List.of(evalCase));

        assertEquals(0, report.metrics().passedCount());
        assertEquals(1, report.metrics().failedCount());
        assertEquals(0.0d, report.metrics().trajectoryPassRate());
        assertEquals("TRAJECTORY_MISMATCH", report.results().get(0).errorCode());
    }

    private AgentTrajectoryEvalCase permissionDeniedCase() {
        String errorCode = AgentToolErrorCode.PERMISSION_DENIED.code();
        return evalCase(
                "tool-permission-denied",
                AgentRunStatus.FAILED,
                errorCode,
                List.of(expectation(AgentToolCallStatus.DENIED, errorCode)),
                List.of(event(AgentToolCallStatus.DENIED, errorCode)));
    }

    private AgentTrajectoryEvalCase confirmationRejectedCase() {
        return evalCase(
                "confirmation-rejected",
                AgentRunStatus.CANCELED,
                AgentToolErrorCode.CONFIRMATION_REJECTED.code(),
                List.of(expectation(
                        AgentToolCallStatus.CONFIRMATION_REQUIRED,
                        AgentToolErrorCode.CONFIRMATION_REQUIRED.code())),
                List.of(event(
                        AgentToolCallStatus.CONFIRMATION_REQUIRED,
                        AgentToolErrorCode.CONFIRMATION_REQUIRED.code())));
    }

    private AgentTrajectoryEvalCase duplicatedConfirmationCase() {
        return evalCase(
                "duplicated-confirmation-does-not-execute-again",
                AgentRunStatus.SUCCEEDED,
                null,
                List.of(
                        expectation(
                                AgentToolCallStatus.CONFIRMATION_REQUIRED,
                                AgentToolErrorCode.CONFIRMATION_REQUIRED.code()),
                        expectation(AgentToolCallStatus.SUCCEEDED, null)),
                List.of(
                        event(
                                AgentToolCallStatus.CONFIRMATION_REQUIRED,
                                AgentToolErrorCode.CONFIRMATION_REQUIRED.code()),
                        event(AgentToolCallStatus.SUCCEEDED, null)));
    }

    private AgentTrajectoryEvalCase evalCase(
            String caseId,
            AgentRunStatus runStatus,
            String runErrorCode,
            List<AgentTrajectoryEventExpectation> expected,
            List<AgentReplayEvent> actual) {
        AgentRunReplayArtifact artifact = new AgentRunReplayArtifact(
                new AgentRunReplayArtifact.RunSummary(
                        91L,
                        "private-request-reference",
                        runStatus.name(),
                        runErrorCode,
                        LocalDateTime.of(2026, 7, 15, 10, 0),
                        LocalDateTime.of(2026, 7, 15, 10, 1)),
                actual,
                new AgentRunReplayArtifact.Metrics(actual.size(), 0, 0, 0));
        return new AgentTrajectoryEvalCase(caseId, runStatus.name(), runErrorCode, expected, artifact);
    }

    private AgentTrajectoryEventExpectation expectation(
            AgentToolCallStatus status,
            String errorCode) {
        return new AgentTrajectoryEventExpectation(
                AgentReplayEventType.TOOL,
                TOOL_NAME,
                status.name(),
                errorCode,
                false,
                INPUT_TYPE);
    }

    private AgentReplayEvent event(
            AgentToolCallStatus status,
            String errorCode) {
        return new AgentReplayEvent(
                AgentReplayEventType.TOOL,
                null,
                TOOL_NAME,
                status.name(),
                errorCode,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                INPUT_TYPE,
                null,
                null,
                LocalDateTime.of(2026, 7, 15, 10, 0),
                LocalDateTime.of(2026, 7, 15, 10, 0));
    }
}
