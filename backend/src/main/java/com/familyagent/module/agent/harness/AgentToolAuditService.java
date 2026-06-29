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
    private static final int SUMMARY_LIMIT = 500;

    private final AgentToolCallRecordRepository repository;

    public void record(
            AgentRunContext context,
            AgentToolDescriptor descriptor,
            Object input,
            AgentToolCallStatus status,
            String errorCode) {
        AgentToolCallRecord record = new AgentToolCallRecord();
        record.setToolName(descriptor == null ? null : descriptor.name());
        record.setFamilyId(context == null ? null : context.familyId());
        record.setViewerUserId(context == null ? null : context.viewerUserId());
        record.setRequestId(context == null ? null : trim(context.requestId(), 128));
        record.setInputSummary(inputSummary(input));
        record.setStatus(status.name());
        record.setErrorCode(trim(errorCode, ERROR_CODE_LIMIT));
        repository.insert(record);
    }

    private String inputSummary(Object input) {
        if (input == null) {
            return "inputType=null";
        }
        return trim("inputType=" + input.getClass().getSimpleName(), SUMMARY_LIMIT);
    }

    private String trim(String value, int limit) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
