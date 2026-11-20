package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.dto.AgentRunEventView;
import com.familyagent.module.agent.dto.AgentRunSummaryView;
import com.familyagent.module.agent.dto.AgentRunView;
import com.familyagent.module.agent.harness.AgentRunTraceQueryService;
import com.familyagent.module.agent.harness.AgentRunQueryService;
import com.familyagent.module.agent.harness.constant.AgentReplayEventType;
import com.familyagent.module.agent.harness.constant.AgentRunStatus;
import com.familyagent.module.agent.harness.dto.AgentRunTrace;
import com.familyagent.module.agent.harness.entity.AgentRunRecord;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentRunViewService {

    private final AgentRunTraceQueryService traceQueryService;
    private final AgentRunQueryService runQueryService;

    public AgentRunView get(Long runId, Long viewerUserId) {
        AgentRunTrace trace = traceQueryService.get(runId);
        AgentRunRecord run = trace.run();
        if (viewerUserId == null || !viewerUserId.equals(run.getViewerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent run is not accessible");
        }
        return new AgentRunView(
                run.getId(),
                run.getSessionId(),
                run.getContextLabel(),
                run.getStatus(),
                run.getErrorCode(),
                AgentRunStatus.FAILED.name().equals(run.getStatus()),
                run.getStartedAt(),
                run.getCompletedAt(),
                events(trace));
    }

    public List<AgentRunSummaryView> listBySession(Long sessionId, Long viewerUserId) {
        if (sessionId == null || viewerUserId == null) {
            return List.of();
        }
        return runQueryService.listBySessionId(sessionId).stream()
                .filter(run -> viewerUserId.equals(run.getViewerUserId()))
                .map(run -> new AgentRunSummaryView(
                        run.getId(),
                        run.getSessionId(),
                        run.getContextLabel(),
                        run.getStatus(),
                        run.getErrorCode(),
                        AgentRunStatus.FAILED.name().equals(run.getStatus()),
                        run.getStartedAt(),
                        run.getCompletedAt()))
                .toList();
    }

    private List<AgentRunEventView> events(AgentRunTrace trace) {
        List<AgentRunEventView> events = new ArrayList<>();
        trace.steps().stream().map(this::stepView).forEach(events::add);
        trace.toolCalls().stream().map(this::toolView).forEach(events::add);
        return events.stream()
                .sorted(Comparator
                        .comparing(AgentRunEventView::startedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AgentRunEventView::eventType)
                        .thenComparing(AgentRunEventView::id,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private AgentRunEventView stepView(AgentRunStepRecord step) {
        return new AgentRunEventView(
                step.getId(),
                AgentReplayEventType.STEP,
                step.getStepType(),
                step.getOperation(),
                step.getStatus(),
                step.getErrorCode(),
                step.getLatencyMs(),
                Boolean.TRUE.equals(step.getDegraded()),
                step.getStartedAt(),
                step.getCompletedAt());
    }

    private AgentRunEventView toolView(AgentToolCallRecord tool) {
        LocalDateTime occurredAt = tool.getCreatedAt();
        return new AgentRunEventView(
                tool.getId(),
                AgentReplayEventType.TOOL,
                null,
                tool.getToolName(),
                tool.getStatus(),
                tool.getErrorCode(),
                null,
                false,
                occurredAt,
                occurredAt);
    }
}
