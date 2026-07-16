package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;
import com.familyagent.module.agent.harness.repository.AgentToolCallRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Writes minimal Agent tool audit records.
 */
@Service
@RequiredArgsConstructor
public class AgentToolAuditService {

    private static final int ERROR_CODE_LIMIT = 80;

    private final AgentToolCallRecordRepository repository;
    private final AgentToolInputSummarizer inputSummarizer;

    public void record(
            AgentRunContext context,
            AgentToolDescriptor descriptor,
            Object input,
            AgentToolCallStatus status,
            String errorCode) {
        record(context, descriptor, input, status, errorCode, null);
    }

    public void record(
            AgentRunContext context,
            AgentToolDescriptor descriptor,
            Object input,
            AgentToolCallStatus status,
            String errorCode,
            Long confirmationId) {
        AgentToolCallRecord record = new AgentToolCallRecord();
        record.setToolName(descriptor == null ? null : descriptor.name());
        record.setRunId(context == null ? null : context.runId());
        record.setFamilyId(context == null ? null : context.familyId());
        record.setViewerUserId(context == null ? null : context.viewerUserId());
        record.setConfirmationId(confirmationId);
        record.setRequestId(context == null ? null : inputSummarizer.trim(context.requestId(), 128));
        record.setInputSummary(inputSummarizer.summarize(input));
        record.setStatus(status.name());
        record.setErrorCode(inputSummarizer.trim(errorCode, ERROR_CODE_LIMIT));
        repository.insert(record);
    }
}
