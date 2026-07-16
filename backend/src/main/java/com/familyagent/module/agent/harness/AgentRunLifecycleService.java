package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentRunStatus;
import com.familyagent.module.agent.harness.entity.AgentRunRecord;
import com.familyagent.module.agent.harness.repository.AgentRunRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentRunLifecycleService {

    private static final int REQUEST_ID_LIMIT = 128;
    private static final int MODE_LIMIT = 80;
    private static final int LABEL_LIMIT = 200;
    private static final int ERROR_CODE_LIMIT = 80;

    private final AgentRunRecordRepository repository;

    @Transactional
    public AgentRunContext startOrResume(AgentRunContext context) {
        if (context == null || context.viewerUserId() == null) {
            return context;
        }
        if (context.runId() != null) {
            transition(context.runId(), AgentRunStatus.RUNNING, null, false);
            return context;
        }

        LocalDateTime now = now();
        AgentRunRecord record = new AgentRunRecord();
        record.setRequestId(trim(context.requestId(), REQUEST_ID_LIMIT));
        record.setFamilyId(context.familyId());
        record.setViewerUserId(context.viewerUserId());
        record.setSessionId(context.sessionId());
        record.setAgentMode(trim(context.agentMode(), MODE_LIMIT));
        record.setSubject(trim(context.subject(), LABEL_LIMIT));
        record.setContextLabel(trim(context.contextLabel(), LABEL_LIMIT));
        record.setStatus(AgentRunStatus.RUNNING.name());
        record.setStartedAt(now);
        repository.insert(record);
        return context.withRunId(record.getId());
    }

    @Transactional
    public void waitForConfirmation(AgentRunContext context) {
        transition(context, AgentRunStatus.WAITING_CONFIRMATION, null, false);
    }

    @Transactional
    public void succeed(AgentRunContext context) {
        transition(context, AgentRunStatus.SUCCEEDED, null, true);
    }

    @Transactional
    public void fail(AgentRunContext context, String errorCode) {
        transition(context, AgentRunStatus.FAILED, errorCode, true);
    }

    @Transactional
    public void cancel(Long runId, String errorCode) {
        transition(runId, AgentRunStatus.CANCELED, errorCode, true);
    }

    @Transactional
    public void fail(Long runId, String errorCode) {
        transition(runId, AgentRunStatus.FAILED, errorCode, true);
    }

    private void transition(
            AgentRunContext context,
            AgentRunStatus status,
            String errorCode,
            boolean terminal) {
        if (context != null) {
            transition(context.runId(), status, errorCode, terminal);
        }
    }

    private void transition(
            Long runId,
            AgentRunStatus status,
            String errorCode,
            boolean terminal) {
        if (runId == null) {
            return;
        }
        AgentRunRecord update = new AgentRunRecord();
        update.setId(runId);
        update.setStatus(status.name());
        update.setErrorCode(trim(errorCode, ERROR_CODE_LIMIT));
        if (terminal) {
            update.setCompletedAt(now());
        }
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
}
