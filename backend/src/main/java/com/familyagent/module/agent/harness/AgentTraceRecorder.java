package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentRunStepStatus;
import com.familyagent.module.agent.harness.dto.AgentTraceSpanDescriptor;
import com.familyagent.module.agent.harness.dto.AgentTraceObservation;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.agent.harness.repository.AgentRunStepRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentTraceRecorder {

    private static final int REQUEST_ID_LIMIT = 128;
    private static final int OPERATION_LIMIT = 120;
    private static final int PROVIDER_LIMIT = 80;
    private static final int MODEL_LIMIT = 160;
    private static final int PROMPT_VERSION_LIMIT = 80;
    private static final int SKILL_VERSION_LIMIT = 40;
    private static final int ERROR_CODE_LIMIT = 80;
    private static final long MAX_OBSERVED_LATENCY_MS = Duration.ofDays(1).toMillis();

    private final AgentRunStepRecordRepository repository;

    @Transactional
    public AgentRunStepRecord start(AgentRunContext context, AgentTraceSpanDescriptor descriptor) {
        if (context == null || context.runId() == null) {
            return null;
        }
        LocalDateTime startedAt = now();
        AgentRunStepRecord record = new AgentRunStepRecord();
        record.setRunId(context.runId());
        record.setRequestId(trim(context.requestId(), REQUEST_ID_LIMIT));
        record.setSpanId(UUID.randomUUID().toString());
        record.setParentSpanId(descriptor.parentSpanId());
        record.setStepType(descriptor.stepType().name());
        record.setOperation(descriptor.operation());
        record.setStatus(AgentRunStepStatus.RUNNING.name());
        record.setProvider(descriptor.provider());
        record.setModel(descriptor.model());
        record.setPromptVersion(descriptor.promptVersion());
        record.setSkillVersion(descriptor.skillVersion());
        record.setDegraded(false);
        record.setPrivacyCategories(descriptor.privacyCategories().stream()
                .map(Enum::name)
                .collect(Collectors.joining(",")));
        record.setStartedAt(startedAt);
        repository.insert(record);
        return record;
    }

    @Transactional
    public void succeed(AgentRunStepRecord record) {
        complete(record, AgentRunStepStatus.SUCCEEDED, null, false);
    }

    @Transactional
    public void fail(AgentRunStepRecord record, String errorCode) {
        complete(record, AgentRunStepStatus.FAILED, errorCode, false);
    }

    @Transactional
    public void failDegraded(AgentRunStepRecord record, String errorCode) {
        complete(record, AgentRunStepStatus.FAILED, errorCode, true);
    }

    @Transactional
    public void recordObservation(AgentRunContext context, AgentTraceObservation observation) {
        if (context == null || context.runId() == null || observation == null) {
            return;
        }
        LocalDateTime completedAt = now();
        long latencyMs = observation.latencyMs() == null
                ? 0
                : Math.min(MAX_OBSERVED_LATENCY_MS, Math.max(0, observation.latencyMs()));
        AgentRunStepRecord record = new AgentRunStepRecord();
        record.setRunId(context.runId());
        record.setRequestId(trim(context.requestId(), REQUEST_ID_LIMIT));
        record.setSpanId(UUID.randomUUID().toString());
        record.setStepType(observation.stepType().name());
        record.setOperation(trim(observation.operation(), OPERATION_LIMIT));
        record.setStatus((observation.success()
                ? AgentRunStepStatus.SUCCEEDED
                : AgentRunStepStatus.FAILED).name());
        record.setProvider(trim(observation.provider(), PROVIDER_LIMIT));
        record.setModel(trim(observation.model(), MODEL_LIMIT));
        record.setPromptVersion(trim(observation.promptVersion(), PROMPT_VERSION_LIMIT));
        record.setSkillVersion(trim(observation.skillVersion(), SKILL_VERSION_LIMIT));
        record.setLatencyMs(latencyMs);
        record.setErrorCode(trim(observation.errorCode(), ERROR_CODE_LIMIT));
        record.setDegraded(observation.degraded());
        record.setPrivacyCategories(privacyCategories(observation.privacyCategories()));
        record.setStartedAt(completedAt.minusNanos(Duration.ofMillis(latencyMs).toNanos()));
        record.setCompletedAt(completedAt);
        repository.insert(record);
    }

    private void complete(
            AgentRunStepRecord record,
            AgentRunStepStatus status,
            String errorCode,
            boolean degraded) {
        if (record == null || record.getId() == null) {
            return;
        }
        LocalDateTime completedAt = now();
        AgentRunStepRecord update = new AgentRunStepRecord();
        update.setId(record.getId());
        update.setStatus(status.name());
        update.setErrorCode(trim(errorCode, ERROR_CODE_LIMIT));
        update.setDegraded(degraded);
        update.setLatencyMs(Math.max(0, Duration.between(record.getStartedAt(), completedAt).toMillis()));
        update.setCompletedAt(completedAt);
        repository.updateById(update);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(Clock.systemDefaultZone());
    }

    private String trim(String value, int limit) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private String privacyCategories(List<?> categories) {
        return categories.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
