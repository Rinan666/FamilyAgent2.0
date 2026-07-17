package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentToolPermissionGateTest {

    @Mock private FamilyMembershipFacade familyMembershipFacade;

    @Test
    void assertAllowed_shouldVerifyExplicitViewerThroughFacade() {
        AgentToolPermissionGate gate = new AgentToolPermissionGate(familyMembershipFacade);

        gate.assertAllowed(context(), descriptor(), new Object());

        verify(familyMembershipFacade).checkMembership(11L, 34L);
    }

    @Test
    void assertAllowed_shouldMapMembershipFailureToPermissionDenied() {
        AgentToolPermissionGate gate = new AgentToolPermissionGate(familyMembershipFacade);
        doThrow(new BusinessException(ErrorCode.NOT_FOUND))
                .when(familyMembershipFacade)
                .checkMembership(11L, 34L);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> gate.assertAllowed(context(), descriptor(), new Object()));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), error.getCode());
        assertEquals("Agent tool permission denied", error.getMessage());
    }

    @Test
    void assertAllowed_shouldRejectIncompleteContextBeforeMembershipLookup() {
        AgentToolPermissionGate gate = new AgentToolPermissionGate(familyMembershipFacade);
        AgentRunContext incomplete = new AgentRunContext(
                "request-1", 11L, null, null, "family", "FamilyAgent", "test");

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> gate.assertAllowed(incomplete, descriptor(), new Object()));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), error.getCode());
        verify(familyMembershipFacade, never()).checkMembership(11L, 34L);
    }

    private static AgentRunContext context() {
        return new AgentRunContext(
                "request-1", 11L, 34L, null, "family", "FamilyAgent", "test");
    }

    private static AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "test_tool",
                "Test tool",
                Object.class,
                Object.class,
                AgentToolSideEffect.READ_ONLY,
                AgentToolConfirmationRequirement.NOT_REQUIRED,
                AgentToolPrivacyLevel.FAMILY_PRIVATE);
    }
}
