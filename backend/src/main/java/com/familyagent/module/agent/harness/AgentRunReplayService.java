package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentReplayEventType;
import com.familyagent.module.agent.harness.constant.AgentRunStepStatus;
import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.dto.AgentReplayEvent;
import com.familyagent.module.agent.harness.dto.AgentRunReplayArtifact;
import com.familyagent.module.agent.harness.dto.AgentRunTrace;
import com.familyagent.module.agent.harness.entity.AgentRunRecord;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentRunReplayService {

    private final AgentRunTraceQueryService traceQueryService;
    private final AgentReplayPrivacyFilter privacyFilter;

    public AgentRunReplayArtifact get(Long runId) {
        AgentRunTrace trace = traceQueryService.get(runId);
        List<AgentReplayEvent> trajectory = trajectory(trace);
        return new AgentRunReplayArtifact(
                runSummary(trace.run()),
                trajectory,
                metrics(trajectory));
    }

    private AgentRunReplayArtifact.RunSummary runSummary(AgentRunRecord run) {
        return new AgentRunReplayArtifact.RunSummary(
                run.getId(),
                privacyFilter.requestRef(run.getRequestId()),
                run.getStatus(),
                run.getErrorCode(),
                run.getStartedAt(),
                run.getCompletedAt());
    }

    private List<AgentReplayEvent> trajectory(AgentRunTrace trace) {
        List<AgentReplayEvent> events = new ArrayList<>();
        trace.steps().stream().map(this::stepEvent).forEach(events::add);
        trace.toolCalls().stream().map(this::toolEvent).forEach(events::add);
        return events.stream()
                .sorted(Comparator
                        .comparing(AgentReplayEvent::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(event -> event.eventType().name())
                        .thenComparing(AgentReplayEvent::operation, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private AgentReplayEvent stepEvent(AgentRunStepRecord step) {
        return new AgentReplayEvent(
                AgentReplayEventType.STEP,
                step.getStepType(),
                step.getOperation(),
                step.getStatus(),
                step.getErrorCode(),
                step.getProvider(),
                step.getModel(),
                step.getPromptVersion(),
                step.getSkillVersion(),
                step.getLatencyMs(),
                Boolean.TRUE.equals(step.getDegraded()),
                step.getPrivacyCategories(),
                null,
                step.getSpanId(),
                step.getParentSpanId(),
                step.getStartedAt(),
                step.getCompletedAt());
    }

    private AgentReplayEvent toolEvent(AgentToolCallRecord tool) {
        return new AgentReplayEvent(
                AgentReplayEventType.TOOL,
                null,
                tool.getToolName(),
                tool.getStatus(),
                tool.getErrorCode(),
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                privacyFilter.inputType(tool.getInputSummary()),
                null,
                null,
                tool.getCreatedAt(),
                tool.getCreatedAt());
    }

    private AgentRunReplayArtifact.Metrics metrics(List<AgentReplayEvent> trajectory) {
        int failed = (int) trajectory.stream().filter(this::failed).count();
        int degraded = (int) trajectory.stream().filter(AgentReplayEvent::degraded).count();
        long totalLatency = trajectory.stream()
                .map(AgentReplayEvent::latencyMs)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        return new AgentRunReplayArtifact.Metrics(trajectory.size(), failed, degraded, totalLatency);
    }

    private boolean failed(AgentReplayEvent event) {
        return AgentRunStepStatus.FAILED.name().equals(event.status())
                || AgentToolCallStatus.FAILED.name().equals(event.status())
                || AgentToolCallStatus.DENIED.name().equals(event.status());
    }
}
