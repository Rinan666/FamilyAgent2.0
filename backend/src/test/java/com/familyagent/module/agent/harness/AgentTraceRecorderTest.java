package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentRunStepStatus;
import com.familyagent.module.agent.harness.constant.AgentRunStepType;
import com.familyagent.module.agent.harness.constant.AgentTracePrivacyCategory;
import com.familyagent.module.agent.harness.dto.AgentTraceObservation;
import com.familyagent.module.agent.harness.dto.AgentTraceSpanDescriptor;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.agent.harness.repository.AgentRunStepRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentTraceRecorderTest {

    private final AgentRunStepRecordRepository repository = mock(AgentRunStepRecordRepository.class);
    private final AgentTraceRecorder recorder = new AgentTraceRecorder(repository);

    @Test
    void startWritesVersionedPrivacySafeSpan() {
        doAnswer(invocation -> {
            AgentRunStepRecord record = invocation.getArgument(0);
            record.setId(501L);
            return 1;
        }).when(repository).insert(any(AgentRunStepRecord.class));
        AgentRunContext context = context();
        AgentTraceSpanDescriptor descriptor = descriptor();

        AgentRunStepRecord span = recorder.start(context, descriptor);

        assertEquals(501L, span.getId());
        assertEquals(91L, span.getRunId());
        assertEquals("request-1", span.getRequestId());
        assertEquals(AgentRunStepType.SKILL.name(), span.getStepType());
        assertEquals("skill.save_memory.plan", span.getOperation());
        assertEquals("memory.save_plan.v1", span.getPromptVersion());
        assertEquals("1.0.0", span.getSkillVersion());
        assertEquals("FAMILY_DATA", span.getPrivacyCategories());
        assertEquals(AgentRunStepStatus.RUNNING.name(), span.getStatus());
        assertNotNull(span.getSpanId());
        assertNotNull(span.getStartedAt());
    }

    @Test
    void failWritesTerminalLatencyAndErrorCode() {
        AgentRunStepRecord span = new AgentRunStepRecord();
        span.setId(501L);
        span.setStartedAt(java.time.LocalDateTime.now().minusSeconds(1));

        recorder.fail(span, "AI_PROVIDER_ERROR");

        ArgumentCaptor<AgentRunStepRecord> captor = ArgumentCaptor.forClass(AgentRunStepRecord.class);
        verify(repository).updateById(captor.capture());
        AgentRunStepRecord update = captor.getValue();
        assertEquals(AgentRunStepStatus.FAILED.name(), update.getStatus());
        assertEquals("AI_PROVIDER_ERROR", update.getErrorCode());
        assertTrue(update.getLatencyMs() >= 0);
        assertNotNull(update.getCompletedAt());
    }

    @Test
    void recordObservationWritesCompletedPrivacySafeSpan() {
        AgentTraceObservation observation = new AgentTraceObservation(
                AgentRunStepType.WEB_SEARCH,
                "web_search.public",
                "tavily",
                null,
                null,
                null,
                25L,
                false,
                "WEB_SEARCH_PROVIDER_ERROR",
                true,
                List.of(AgentTracePrivacyCategory.PUBLIC_DATA));

        recorder.recordObservation(context(), observation);

        ArgumentCaptor<AgentRunStepRecord> captor = ArgumentCaptor.forClass(AgentRunStepRecord.class);
        verify(repository).insert(captor.capture());
        AgentRunStepRecord record = captor.getValue();
        assertEquals(91L, record.getRunId());
        assertEquals(AgentRunStepType.WEB_SEARCH.name(), record.getStepType());
        assertEquals("web_search.public", record.getOperation());
        assertEquals(AgentRunStepStatus.FAILED.name(), record.getStatus());
        assertEquals("tavily", record.getProvider());
        assertEquals(25L, record.getLatencyMs());
        assertEquals("WEB_SEARCH_PROVIDER_ERROR", record.getErrorCode());
        assertTrue(record.getDegraded());
        assertEquals("PUBLIC_DATA", record.getPrivacyCategories());
        assertEquals(25L, Duration.between(record.getStartedAt(), record.getCompletedAt()).toMillis());
    }

    @Test
    void failDegradedMarksFailedFallbackSpan() {
        AgentRunStepRecord span = new AgentRunStepRecord();
        span.setId(501L);
        span.setStartedAt(java.time.LocalDateTime.now().minusSeconds(1));

        recorder.failDegraded(span, "MEMORY_RECALL_FAILED");

        ArgumentCaptor<AgentRunStepRecord> captor = ArgumentCaptor.forClass(AgentRunStepRecord.class);
        verify(repository).updateById(captor.capture());
        AgentRunStepRecord update = captor.getValue();
        assertEquals(AgentRunStepStatus.FAILED.name(), update.getStatus());
        assertEquals("MEMORY_RECALL_FAILED", update.getErrorCode());
        assertTrue(update.getDegraded());
    }

    private AgentRunContext context() {
        return new AgentRunContext(
                91L,
                "request-1",
                10L,
                101L,
                null,
                "family_memory",
                "FamilyAgent",
                "save_memory_plan",
                true);
    }

    private AgentTraceSpanDescriptor descriptor() {
        return new AgentTraceSpanDescriptor(
                AgentRunStepType.SKILL,
                "skill.save_memory.plan",
                null,
                null,
                null,
                "memory.save_plan.v1",
                "1.0.0",
                List.of(AgentTracePrivacyCategory.FAMILY_DATA));
    }
}
