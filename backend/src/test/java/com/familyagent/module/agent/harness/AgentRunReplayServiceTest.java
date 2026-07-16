package com.familyagent.module.agent.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.module.agent.harness.constant.AgentReplayEventType;
import com.familyagent.module.agent.harness.constant.AgentRunStepStatus;
import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.dto.AgentRunReplayArtifact;
import com.familyagent.module.agent.harness.dto.AgentRunTrace;
import com.familyagent.module.agent.harness.entity.AgentRunRecord;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunReplayServiceTest {

    private final AgentRunTraceQueryService traceQueryService = mock(AgentRunTraceQueryService.class);
    private final AgentRunReplayService service = new AgentRunReplayService(
            traceQueryService,
            new AgentReplayPrivacyFilter());

    @Test
    void getBuildsChronologicalPrivacySafeReplayArtifact() throws Exception {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 15, 10, 0);
        AgentRunRecord run = new AgentRunRecord();
        run.setId(91L);
        run.setRequestId("private-request-reference");
        run.setFamilyId(10L);
        run.setViewerUserId(101L);
        run.setSubject("private family subject");
        run.setStatus("FAILED");
        run.setErrorCode("AI_PROVIDER_ERROR");
        run.setStartedAt(startedAt);
        run.setCompletedAt(startedAt.plusSeconds(3));

        AgentToolCallRecord tool = new AgentToolCallRecord();
        tool.setRunId(91L);
        tool.setToolName("recall_family_memory");
        tool.setInputSummary("child said a private sentence");
        tool.setStatus(AgentToolCallStatus.SUCCEEDED.name());
        tool.setCreatedAt(startedAt.plusSeconds(1));

        AgentRunStepRecord step = new AgentRunStepRecord();
        step.setRunId(91L);
        step.setSpanId("span-1");
        step.setStepType("EMBEDDING");
        step.setOperation("embedding.recall_query");
        step.setStatus(AgentRunStepStatus.FAILED.name());
        step.setErrorCode("AI_EMBEDDING_UNAVAILABLE");
        step.setProvider("dashscope");
        step.setModel("dashscope/text-embedding-v4");
        step.setLatencyMs(21L);
        step.setDegraded(true);
        step.setPrivacyCategories("FAMILY_DATA");
        step.setStartedAt(startedAt.plusSeconds(2));
        step.setCompletedAt(startedAt.plusSeconds(3));

        when(traceQueryService.get(91L)).thenReturn(new AgentRunTrace(
                run,
                List.of(step),
                List.of(tool)));

        AgentRunReplayArtifact artifact = service.get(91L);

        assertEquals(91L, artifact.run().runId());
        assertNotEquals(run.getRequestId(), artifact.run().requestRef());
        assertEquals(2, artifact.trajectory().size());
        assertEquals(AgentReplayEventType.TOOL, artifact.trajectory().get(0).eventType());
        assertEquals("inputType=REDACTED", artifact.trajectory().get(0).inputType());
        assertEquals(AgentReplayEventType.STEP, artifact.trajectory().get(1).eventType());
        assertEquals(2, artifact.metrics().eventCount());
        assertEquals(1, artifact.metrics().failedEventCount());
        assertEquals(1, artifact.metrics().degradedEventCount());
        assertEquals(21L, artifact.metrics().totalLatencyMs());

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(artifact);
        assertFalse(json.contains("private-request-reference"));
        assertFalse(json.contains("private family subject"));
        assertFalse(json.contains("child said a private sentence"));
        assertFalse(json.contains("familyId"));
        assertFalse(json.contains("viewerUserId"));
        assertTrue(json.contains("embedding.recall_query"));
    }

    @Test
    void getKeepsOnlyWhitelistedInputTypeSummary() {
        AgentRunRecord run = new AgentRunRecord();
        run.setId(91L);
        AgentToolCallRecord tool = new AgentToolCallRecord();
        tool.setInputSummary("inputType=RecallFamilyMemoryInput");
        tool.setStatus(AgentToolCallStatus.SUCCEEDED.name());
        when(traceQueryService.get(91L)).thenReturn(new AgentRunTrace(run, List.of(), List.of(tool)));

        AgentRunReplayArtifact artifact = service.get(91L);

        assertEquals("inputType=RecallFamilyMemoryInput", artifact.trajectory().get(0).inputType());
    }
}
