package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.harness.constant.AgentConfirmationStatus;
import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolErrorCode;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.dto.AgentToolCallRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.harness.entity.AgentToolConfirmationRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentToolExecutorTest {

    @Mock private AgentToolRegistry registry;
    @Mock private AgentToolPermissionGate permissionGate;
    @Mock private AgentToolAuditService auditService;
    @Mock private AgentConfirmationPolicy confirmationPolicy;
    @Mock private AgentToolConfirmationService confirmationService;
    private final AgentToolInputValidator inputValidator = new AgentToolInputValidator();
    private final AgentToolErrorMapper errorMapper = new AgentToolErrorMapper();
    private final AgentToolDescriptorFactory descriptorFactory = new AgentToolDescriptorFactory();

    private final AgentRunContext context = new AgentRunContext(
            "req-1",
            10L,
            101L,
            201L,
            "family_memory",
            "family",
            "test");

    @Test
    void execute_success_passesGateAndWritesAudit() {
        EchoTool tool = new EchoTool();
        EchoInput input = new EchoInput("hello");
        doReturn(tool).when(registry).require(EchoTool.NAME);
        when(confirmationPolicy.evaluate(context, tool.descriptor(), input))
                .thenReturn(AgentConfirmationStatus.NOT_REQUIRED);
        AgentToolExecutor executor = executor();

        AgentToolCallResult<EchoOutput> result = executor.execute(new AgentToolCallRequest<>(
                EchoTool.NAME,
                context,
                input));

        assertTrue(result.success());
        assertEquals("hello", result.data().value());
        verify(permissionGate).assertAllowed(context, tool.descriptor(), input);
        verify(auditService).record(context, tool.descriptor(), input, AgentToolCallStatus.SUCCEEDED, null);
    }

    @Test
    void execute_permissionDenied_returnsStructuredDeniedAndWritesAudit() {
        EchoTool tool = new EchoTool();
        EchoInput input = new EchoInput("hello");
        doReturn(tool).when(registry).require(EchoTool.NAME);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "denied"))
                .when(permissionGate)
                .assertAllowed(context, tool.descriptor(), input);
        AgentToolExecutor executor = executor();

        AgentToolCallResult<EchoOutput> result = executor.execute(new AgentToolCallRequest<>(
                EchoTool.NAME,
                context,
                input));

        assertFalse(result.success());
        assertEquals(AgentToolCallStatus.DENIED, result.status());
        assertEquals(AgentToolErrorCode.PERMISSION_DENIED.code(), result.errorCode());
        verify(auditService).record(
                context,
                tool.descriptor(),
                input,
                AgentToolCallStatus.DENIED,
                AgentToolErrorCode.PERMISSION_DENIED.code());
    }

    @Test
    void execute_confirmationRequired_returnsStructuredPendingAndWritesAudit() {
        EchoTool tool = new EchoTool();
        EchoInput input = new EchoInput("hello");
        doReturn(tool).when(registry).require(EchoTool.NAME);
        when(confirmationPolicy.evaluate(context, tool.descriptor(), input))
                .thenReturn(AgentConfirmationStatus.REQUIRED);
        AgentToolConfirmationRecord confirmation = new AgentToolConfirmationRecord();
        confirmation.setId(55L);
        when(confirmationService.createRequired(context, tool.descriptor(), input))
                .thenReturn(confirmation);
        AgentToolExecutor executor = executor();

        AgentToolCallResult<EchoOutput> result = executor.execute(new AgentToolCallRequest<>(
                EchoTool.NAME,
                context,
                input));

        assertFalse(result.success());
        assertEquals(AgentToolCallStatus.CONFIRMATION_REQUIRED, result.status());
        assertEquals(AgentToolErrorCode.CONFIRMATION_REQUIRED.code(), result.errorCode());
        assertEquals(55L, result.confirmationId());
        verify(confirmationService).createRequired(context, tool.descriptor(), input);
        verify(auditService).record(
                context,
                tool.descriptor(),
                input,
                AgentToolCallStatus.CONFIRMATION_REQUIRED,
                AgentToolErrorCode.CONFIRMATION_REQUIRED.code());
    }

    @Test
    void execute_invalidInput_returnsStructuredFailureAndWritesAudit() {
        EchoTool tool = new EchoTool();
        doReturn(tool).when(registry).require(EchoTool.NAME);
        AgentToolExecutor executor = executor();

        AgentToolCallResult<EchoOutput> result = executor.execute(new AgentToolCallRequest<>(
                EchoTool.NAME,
                context,
                "wrong-input"));

        assertFalse(result.success());
        assertEquals(AgentToolCallStatus.FAILED, result.status());
        assertEquals(AgentToolErrorCode.INVALID_INPUT.code(), result.errorCode());
        verify(auditService).record(
                eq(context),
                eq(tool.descriptor()),
                eq("wrong-input"),
                eq(AgentToolCallStatus.FAILED),
                eq(AgentToolErrorCode.INVALID_INPUT.code()));
    }

    @Test
    void execute_unknownTool_returnsStructuredFailureAndWritesAudit() {
        when(registry.require("missing"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "missing"));
        AgentToolExecutor executor = executor();

        AgentToolCallResult<EchoOutput> result = executor.execute(new AgentToolCallRequest<>(
                "missing",
                context,
                new EchoInput("hello")));

        assertFalse(result.success());
        assertEquals(AgentToolErrorCode.TOOL_NOT_FOUND.code(), result.errorCode());
        ArgumentCaptor<AgentToolDescriptor> descriptorCaptor = ArgumentCaptor.forClass(AgentToolDescriptor.class);
        verify(auditService).record(
                eq(context),
                descriptorCaptor.capture(),
                any(),
                eq(AgentToolCallStatus.FAILED),
                eq(AgentToolErrorCode.TOOL_NOT_FOUND.code()));
        assertEquals("missing", descriptorCaptor.getValue().name());
    }

    private AgentToolExecutor executor() {
        return new AgentToolExecutor(
                registry,
                permissionGate,
                auditService,
                inputValidator,
                confirmationPolicy,
                confirmationService,
                errorMapper,
                descriptorFactory);
    }

    private record EchoInput(String value) {
    }

    private record EchoOutput(String value) {
    }

    private static class EchoTool implements AgentTool<EchoInput, EchoOutput> {

        private static final String NAME = "echo";
        private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
                NAME,
                "Echo test tool",
                EchoInput.class,
                EchoOutput.class,
                AgentToolSideEffect.READ_ONLY,
                AgentToolConfirmationRequirement.NOT_REQUIRED,
                AgentToolPrivacyLevel.INTERNAL_ONLY);

        @Override
        public AgentToolDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public Class<EchoInput> inputType() {
            return EchoInput.class;
        }

        @Override
        public EchoOutput execute(AgentRunContext context, EchoInput input) {
            return new EchoOutput(input.value());
        }
    }
}
