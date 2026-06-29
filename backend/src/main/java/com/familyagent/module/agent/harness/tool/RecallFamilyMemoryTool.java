package com.familyagent.module.agent.harness.tool;

import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentTool;
import com.familyagent.module.agent.harness.AgentToolDescriptor;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.dto.RecallFamilyMemoryInput;
import com.familyagent.module.agent.harness.dto.RecallFamilyMemoryOutput;
import com.familyagent.module.memory.facade.AgentMemoryContextFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecallFamilyMemoryTool implements AgentTool<RecallFamilyMemoryInput, RecallFamilyMemoryOutput> {

    public static final String NAME = "recall_family_memory";

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            NAME,
            "Recall authorized family memory context for a FamilyAgent request",
            RecallFamilyMemoryInput.class,
            RecallFamilyMemoryOutput.class,
            AgentToolSideEffect.READ_ONLY,
            AgentToolConfirmationRequirement.NOT_REQUIRED,
            AgentToolPrivacyLevel.FAMILY_PRIVATE);

    private final AgentMemoryContextFacade memoryContextFacade;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<RecallFamilyMemoryInput> inputType() {
        return RecallFamilyMemoryInput.class;
    }

    @Override
    public RecallFamilyMemoryOutput execute(AgentRunContext context, RecallFamilyMemoryInput input) {
        String memoryContext = memoryContextFacade.buildFamilyAgentContext(
                context.familyId(),
                context.viewerUserId(),
                input.memberMessage(),
                input.recentUserMessages());
        return new RecallFamilyMemoryOutput(memoryContext);
    }
}
