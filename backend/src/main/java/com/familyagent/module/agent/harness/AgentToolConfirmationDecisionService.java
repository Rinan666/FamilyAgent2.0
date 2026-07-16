package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.harness.constant.AgentConfirmationDecision;
import com.familyagent.module.agent.harness.constant.AgentConfirmationStatus;
import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.constant.AgentToolErrorCode;
import com.familyagent.module.agent.harness.dto.AgentToolCallRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.harness.dto.AgentToolConfirmationDecisionResult;
import com.familyagent.module.agent.harness.dto.AgentToolConfirmationVO;
import com.familyagent.module.agent.harness.entity.AgentToolConfirmationRecord;
import com.familyagent.module.agent.harness.repository.AgentToolConfirmationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentToolConfirmationDecisionService {

    private final AgentToolConfirmationRecordRepository repository;
    private final AgentToolRegistry registry;
    private final AgentToolConfirmationPayloadCodec payloadCodec;
    private final AgentToolExecutor toolExecutor;
    private final AgentRunLifecycleService runLifecycleService;

    @Transactional
    public AgentToolConfirmationDecisionResult decide(
            Long confirmationId,
            Long viewerUserId,
            AgentConfirmationDecision decision) {
        if (decision == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent tool confirmation decision is required");
        }
        AgentToolConfirmationRecord record = repository.selectByIdForUpdate(confirmationId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent tool confirmation not found");
        }
        if (viewerUserId == null || !viewerUserId.equals(record.getViewerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent tool confirmation belongs to another user");
        }

        AgentConfirmationStatus current = AgentConfirmationStatus.valueOf(record.getStatus());
        if (current != AgentConfirmationStatus.REQUIRED) {
            return result(record, null);
        }

        LocalDateTime now = LocalDateTime.now(Clock.systemDefaultZone());
        if (!record.getExpiresAt().isAfter(now)) {
            transition(record, AgentConfirmationStatus.EXPIRED, now, null);
            runLifecycleService.fail(record.getRunId(), AgentToolErrorCode.CONFIRMATION_EXPIRED.code());
            return result(record, null);
        }
        if (decision == AgentConfirmationDecision.REJECT) {
            transition(record, AgentConfirmationStatus.REJECTED, now, null);
            runLifecycleService.cancel(record.getRunId(), AgentToolErrorCode.CONFIRMATION_REJECTED.code());
            return result(record, null);
        }

        AgentToolCallResult<?> toolResult = executeApproved(record);
        transition(record, AgentConfirmationStatus.APPROVED, now, toolResult);
        return result(record, toolResult);
    }

    private AgentToolCallResult<?> executeApproved(AgentToolConfirmationRecord record) {
        try {
            AgentTool<?, ?> rawTool = registry.require(record.getToolName());
            return executeTyped(record, rawTool);
        } catch (BusinessException e) {
            String errorCode = e.getCode() == ErrorCode.NOT_FOUND.getCode()
                    ? AgentToolErrorCode.TOOL_NOT_FOUND.code()
                    : AgentToolErrorCode.INVALID_INPUT.code();
            runLifecycleService.fail(record.getRunId(), errorCode);
            return AgentToolCallResult.failure(
                    AgentToolCallStatus.FAILED,
                    errorCode,
                    e.getMessage(),
                    false);
        }
    }

    private <I, O> AgentToolCallResult<O> executeTyped(
            AgentToolConfirmationRecord record,
            AgentTool<I, O> tool) {
        I input = payloadCodec.decode(tool, record.getInputPayload());
        AgentRunContext context = new AgentRunContext(
                record.getRunId(),
                record.getRequestId(),
                record.getFamilyId(),
                record.getViewerUserId(),
                record.getSessionId(),
                record.getAgentMode(),
                record.getSubject(),
                record.getContextLabel(),
                !Boolean.FALSE.equals(record.getCompleteRunAfterTool()));
        return toolExecutor.executeConfirmed(
                new AgentToolCallRequest<>(record.getToolName(), context, input),
                record.getId());
    }

    private void transition(
            AgentToolConfirmationRecord record,
            AgentConfirmationStatus next,
            LocalDateTime decidedAt,
            AgentToolCallResult<?> toolResult) {
        record.setStatus(next.name());
        record.setDecidedAt(decidedAt);
        if (toolResult != null) {
            record.setExecutedAt(LocalDateTime.now(Clock.systemDefaultZone()));
            record.setExecutionStatus(toolResult.status().name());
            record.setExecutionErrorCode(toolResult.errorCode());
        }
        repository.updateById(record);
    }

    private AgentToolConfirmationDecisionResult result(
            AgentToolConfirmationRecord record,
            AgentToolCallResult<?> toolResult) {
        return new AgentToolConfirmationDecisionResult(AgentToolConfirmationVO.from(record), toolResult);
    }
}
