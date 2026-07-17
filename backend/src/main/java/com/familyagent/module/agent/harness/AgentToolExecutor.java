package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.agent.harness.constant.AgentConfirmationStatus;
import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.constant.AgentToolErrorCode;
import com.familyagent.module.agent.harness.dto.AgentToolCallRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.harness.entity.AgentToolConfirmationRecord;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;
import com.familyagent.module.agent.harness.provenance.AgentRecordProvenanceWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single execution entry point for Agent tools.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolExecutor {

    private final AgentToolRegistry registry;
    private final AgentRunLifecycleService runLifecycleService;
    private final AgentToolPermissionGate permissionGate;
    private final AgentToolAuditService auditService;
    private final AgentToolInputValidator inputValidator;
    private final AgentConfirmationPolicy confirmationPolicy;
    private final AgentToolConfirmationService confirmationService;
    private final AgentToolErrorMapper errorMapper;
    private final AgentToolDescriptorFactory descriptorFactory;
    private final AgentRecordProvenanceWriter provenanceWriter;

    @Transactional
    public <I, O> AgentToolCallResult<O> execute(AgentToolCallRequest<I> request) {
        return executeInternal(request, true, null);
    }

    @Transactional
    public <I, O> AgentToolCallResult<O> executeConfirmed(
            AgentToolCallRequest<I> request,
            Long confirmationId) {
        return executeInternal(request, false, confirmationId);
    }

    private <I, O> AgentToolCallResult<O> executeInternal(
            AgentToolCallRequest<I> request,
            boolean evaluateConfirmation,
            Long confirmationId) {
        if (request == null) {
            return AgentToolCallResult.failure(
                    AgentToolCallStatus.FAILED,
                    AgentToolErrorCode.INVALID_INPUT.code(),
                    "Agent tool request is required",
                    false);
        }
        AgentRunContext context = runLifecycleService.startOrResume(request.context());
        request = new AgentToolCallRequest<>(request.toolName(), context, request.input());
        boolean completeRunAfterTool = context == null || context.completeRunAfterTool();
        AgentTool<?, ?> rawTool;
        try {
            rawTool = registry.require(request.toolName());
        } catch (BusinessException e) {
            recordAudit(request.context(), descriptorFactory.unknown(request.toolName()), request.input(),
                    AgentToolCallStatus.FAILED,
                    AgentToolErrorCode.TOOL_NOT_FOUND.code(),
                    confirmationId);
            failRunIfOwned(request.context(), AgentToolErrorCode.TOOL_NOT_FOUND.code(), completeRunAfterTool);
            return AgentToolCallResult.failure(
                    AgentToolCallStatus.FAILED,
                    AgentToolErrorCode.TOOL_NOT_FOUND.code(),
                    e.getMessage(),
                    false);
        }

        AgentToolDescriptor descriptor = rawTool.descriptor();
        try {
            inputValidator.validate(rawTool, request.input());
            permissionGate.assertAllowed(request.context(), descriptor, request.input());
            if (evaluateConfirmation && confirmationPolicy.evaluate(
                    request.context(), descriptor, request.input()) == AgentConfirmationStatus.REQUIRED) {
                AgentToolConfirmationRecord confirmation = confirmationService.createRequired(
                        request.context(), descriptor, request.input());
                auditService.record(request.context(), descriptor, request.input(),
                        AgentToolCallStatus.CONFIRMATION_REQUIRED,
                        AgentToolErrorCode.CONFIRMATION_REQUIRED.code(),
                        confirmation.getId());
                if (completeRunAfterTool) {
                    runLifecycleService.waitForConfirmation(request.context());
                }
                return AgentToolCallResult.confirmationRequired(
                        AgentToolErrorCode.CONFIRMATION_REQUIRED.code(),
                        "Agent tool requires confirmation",
                        confirmation.getId());
            }

            @SuppressWarnings("unchecked")
            AgentTool<I, O> tool = (AgentTool<I, O>) rawTool;
            O output = tool.execute(request.context(), request.input());
            AgentToolCallRecord toolCall = recordAudit(
                    request.context(),
                    descriptor,
                    request.input(),
                    AgentToolCallStatus.SUCCEEDED,
                    null,
                    confirmationId);
            provenanceWriter.recordCreatedOutput(request.context(), descriptor, toolCall, output);
            if (completeRunAfterTool) {
                runLifecycleService.succeed(request.context());
            }
            return AgentToolCallResult.success(output);
        } catch (BusinessException e) {
            String errorCode = errorMapper.errorCode(e);
            AgentToolCallStatus status = errorMapper.status(e);
            recordAudit(request.context(), descriptor, request.input(), status, errorCode, confirmationId);
            failRunIfOwned(request.context(), errorCode, completeRunAfterTool);
            return AgentToolCallResult.failure(status, errorCode, e.getMessage(), false);
        } catch (RuntimeException e) {
            log.warn("Agent tool execution failed: toolName={}", descriptor.name(), e);
            recordAudit(request.context(), descriptor, request.input(),
                    AgentToolCallStatus.FAILED,
                    AgentToolErrorCode.EXECUTION_FAILED.code(),
                    confirmationId);
            failRunIfOwned(request.context(), AgentToolErrorCode.EXECUTION_FAILED.code(), completeRunAfterTool);
            return AgentToolCallResult.failure(
                    AgentToolCallStatus.FAILED,
                    AgentToolErrorCode.EXECUTION_FAILED.code(),
                    "Agent tool execution failed",
                    true);
        }
    }

    private AgentToolCallRecord recordAudit(
            AgentRunContext context,
            AgentToolDescriptor descriptor,
            Object input,
            AgentToolCallStatus status,
            String errorCode,
            Long confirmationId) {
        if (confirmationId == null) {
            return auditService.record(context, descriptor, input, status, errorCode);
        }
        return auditService.record(context, descriptor, input, status, errorCode, confirmationId);
    }

    private void failRunIfOwned(AgentRunContext context, String errorCode, boolean completeRunAfterTool) {
        if (completeRunAfterTool) {
            runLifecycleService.fail(context, errorCode);
        }
    }
}
