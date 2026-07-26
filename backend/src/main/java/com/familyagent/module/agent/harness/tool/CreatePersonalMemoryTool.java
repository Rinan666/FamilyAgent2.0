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
import com.familyagent.module.agent.harness.dto.CreatePersonalMemoryInput;
import com.familyagent.module.agent.harness.dto.CreatePersonalMemoryOutput;
import com.familyagent.module.memory.dto.CreatePersonalMemoryRequest;
import com.familyagent.module.memory.dto.PersonalMemoryView;
import com.familyagent.module.memory.dto.WriteMemoryMetadata;
import com.familyagent.module.memory.facade.AgentPersonalMemoryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CreatePersonalMemoryTool implements AgentTool<CreatePersonalMemoryInput, CreatePersonalMemoryOutput> {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            AgentToolName.CREATE_PERSONAL_MEMORY.value(),
            "Create a personal memory after user confirmation",
            CreatePersonalMemoryInput.class,
            CreatePersonalMemoryOutput.class,
            AgentToolSideEffect.WRITE,
            AgentToolConfirmationRequirement.REQUIRED,
            AgentToolPrivacyLevel.USER_PRIVATE);

    private final AgentPersonalMemoryFacade personalMemoryFacade;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<CreatePersonalMemoryInput> inputType() {
        return CreatePersonalMemoryInput.class;
    }

    @Override
    public CreatePersonalMemoryOutput execute(AgentRunContext context, CreatePersonalMemoryInput input) {
        if (input.content() == null || input.content().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Personal memory content is required");
        }
        CreatePersonalMemoryRequest request = new CreatePersonalMemoryRequest();
        request.setContent(input.content());
        request.setType(input.type());
        request.setVisibility(input.visibility());
        request.setSummary(input.summary());
        request.setImportance(input.importance());
        request.setSelectedFamilyIds(input.selectedFamilyIds());
        if (input.metadata() != null) {
            request.setMetadata(WriteMemoryMetadata.fromMap(input.metadata().toMap()));
        }
        PersonalMemoryView saved = personalMemoryFacade.create(request);
        return new CreatePersonalMemoryOutput(saved.id());
    }
}
