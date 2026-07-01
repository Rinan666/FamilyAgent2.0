package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentConfirmationStatus;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentConfirmationPolicyTest {

    private final AgentConfirmationPolicy policy = new AgentConfirmationPolicy();

    private final AgentRunContext context = new AgentRunContext(
            "req-1",
            10L,
            101L,
            201L,
            "family_memory",
            "family",
            "test");

    @Test
    void evaluate_requiredDescriptor_returnsRequired() {
        AgentToolDescriptor descriptor = descriptor(AgentToolConfirmationRequirement.REQUIRED);

        AgentConfirmationStatus status = policy.evaluate(context, descriptor, new TestInput("value"));

        assertEquals(AgentConfirmationStatus.REQUIRED, status);
    }

    @Test
    void evaluate_readOnlyDescriptor_returnsNotRequired() {
        AgentToolDescriptor descriptor = descriptor(AgentToolConfirmationRequirement.NOT_REQUIRED);

        AgentConfirmationStatus status = policy.evaluate(context, descriptor, new TestInput("value"));

        assertEquals(AgentConfirmationStatus.NOT_REQUIRED, status);
    }

    private AgentToolDescriptor descriptor(AgentToolConfirmationRequirement confirmationRequirement) {
        return new AgentToolDescriptor(
                "test_tool",
                "Test tool",
                TestInput.class,
                TestOutput.class,
                AgentToolSideEffect.READ_ONLY,
                confirmationRequirement,
                AgentToolPrivacyLevel.INTERNAL_ONLY);
    }

    private record TestInput(String value) {
    }

    private record TestOutput(String value) {
    }
}
