package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolErrorCode;
import com.familyagent.module.agent.harness.dto.AgentToolCallRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
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
    private final AgentToolPermissionGate permissionGate;
    private final AgentToolAuditService auditService;

    @Transactional
    public <I, O> AgentToolCallResult<O> execute(AgentToolCallRequest<I> request) {
        if (request == null) {
            return AgentToolCallResult.failure(
                    AgentToolCallStatus.FAILED,
                    AgentToolErrorCode.INVALID_INPUT.code(),
                    "Agent tool request is required",
                    false);
        }
        AgentTool<?, ?> rawTool;
        try {
            rawTool = registry.require(request.toolName());
        } catch (BusinessException e) {
            auditService.record(request.context(), unknownDescriptor(request.toolName()), request.input(),
                    AgentToolCallStatus.FAILED,
                    AgentToolErrorCode.TOOL_NOT_FOUND.code());
            return AgentToolCallResult.failure(
                    AgentToolCallStatus.FAILED,
                    AgentToolErrorCode.TOOL_NOT_FOUND.code(),
                    e.getMessage(),
                    false);
        }

        AgentToolDescriptor descriptor = rawTool.descriptor();
        try {
            validateInput(rawTool, request.input());
            permissionGate.assertAllowed(request.context(), descriptor, request.input());
            if (descriptor.confirmationRequirement() == AgentToolConfirmationRequirement.REQUIRED) {
                auditService.record(request.context(), descriptor, request.input(),
                        AgentToolCallStatus.CONFIRMATION_REQUIRED,
                        AgentToolErrorCode.CONFIRMATION_REQUIRED.code());
                return AgentToolCallResult.failure(
                        AgentToolCallStatus.CONFIRMATION_REQUIRED,
                        AgentToolErrorCode.CONFIRMATION_REQUIRED.code(),
                        "Agent tool requires confirmation",
                        false);
            }

            @SuppressWarnings("unchecked")
            AgentTool<I, O> tool = (AgentTool<I, O>) rawTool;
            O output = tool.execute(request.context(), request.input());
            auditService.record(request.context(), descriptor, request.input(), AgentToolCallStatus.SUCCEEDED, null);
            return AgentToolCallResult.success(output);
        } catch (BusinessException e) {
            String errorCode = businessErrorCode(e);
            AgentToolCallStatus status = e.getCode() == ErrorCode.FORBIDDEN.getCode()
                    ? AgentToolCallStatus.DENIED
                    : AgentToolCallStatus.FAILED;
            auditService.record(request.context(), descriptor, request.input(), status, errorCode);
            return AgentToolCallResult.failure(status, errorCode, e.getMessage(), false);
        } catch (RuntimeException e) {
            log.warn("Agent tool execution failed: toolName={}", descriptor.name(), e);
            auditService.record(request.context(), descriptor, request.input(),
                    AgentToolCallStatus.FAILED,
                    AgentToolErrorCode.EXECUTION_FAILED.code());
            return AgentToolCallResult.failure(
                    AgentToolCallStatus.FAILED,
                    AgentToolErrorCode.EXECUTION_FAILED.code(),
                    "Agent tool execution failed",
                    true);
        }
    }

    private void validateInput(AgentTool<?, ?> tool, Object input) {
        if (input == null || !tool.inputType().isInstance(input)) {
            throw new BusinessException(
                    AgentToolErrorCode.INVALID_INPUT.businessCode(),
                    "Agent tool input type is invalid");
        }
    }

    private String businessErrorCode(BusinessException e) {
        if (e.getCode() == AgentToolErrorCode.INVALID_INPUT.businessCode()) {
            return AgentToolErrorCode.INVALID_INPUT.code();
        }
        if (e.getCode() == ErrorCode.FORBIDDEN.getCode()) {
            return AgentToolErrorCode.PERMISSION_DENIED.code();
        }
        return "BUSINESS_" + e.getCode();
    }

    private AgentToolDescriptor unknownDescriptor(String toolName) {
        return new AgentToolDescriptor(
                toolName,
                "Unknown Agent tool",
                Object.class,
                Object.class,
                com.familyagent.module.agent.harness.constant.AgentToolSideEffect.READ_ONLY,
                AgentToolConfirmationRequirement.NOT_REQUIRED,
                com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel.INTERNAL_ONLY);
    }
}
