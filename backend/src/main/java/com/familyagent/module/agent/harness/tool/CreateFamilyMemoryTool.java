package com.familyagent.module.agent.harness.tool;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentTool;
import com.familyagent.module.agent.harness.AgentToolDescriptor;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.dto.CreateFamilyMemoryInput;
import com.familyagent.module.agent.harness.dto.CreateFamilyMemoryOutput;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.dto.WriteMemoryMetadata;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.facade.AgentFamilyMemoryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CreateFamilyMemoryTool implements AgentTool<CreateFamilyMemoryInput, CreateFamilyMemoryOutput> {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            AgentToolName.CREATE_FAMILY_MEMORY.value(),
            "Create a family memory after user confirmation",
            CreateFamilyMemoryInput.class,
            CreateFamilyMemoryOutput.class,
            AgentToolSideEffect.WRITE,
            AgentToolConfirmationRequirement.REQUIRED,
            AgentToolPrivacyLevel.FAMILY_PRIVATE);

    private final AgentFamilyMemoryFacade familyMemoryFacade;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<CreateFamilyMemoryInput> inputType() {
        return CreateFamilyMemoryInput.class;
    }

    @Override
    public CreateFamilyMemoryOutput execute(AgentRunContext context, CreateFamilyMemoryInput input) {
        if (input.content() == null || input.content().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Family memory content is required");
        }
        CreateFamilyMemoryRequest request = new CreateFamilyMemoryRequest();
        request.setFamilyId(context.familyId());
        request.setContent(input.content());
        request.setType(input.type());
        request.setScope(input.scope());
        request.setSummary(input.summary());
        request.setImportance(input.importance());
        request.setRelatedUserId(input.relatedUserId());
        request.setTags(input.tags());
        if (input.metadata() != null) {
            request.setMetadata(WriteMemoryMetadata.fromMap(input.metadata().toMap()));
        }
        MemoryEntry entry = familyMemoryFacade.create(request);
        return new CreateFamilyMemoryOutput(entry.getId());
    }
}
