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
import com.familyagent.module.agent.harness.dto.CreateDiaryEntryInput;
import com.familyagent.module.agent.harness.dto.CreateDiaryEntryOutput;
import com.familyagent.module.diary.dto.CreateDiaryEntryRequest;
import com.familyagent.module.diary.dto.DiaryEntryMetadata;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.AgentDiaryEntryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CreateDiaryEntryTool implements AgentTool<CreateDiaryEntryInput, CreateDiaryEntryOutput> {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            AgentToolName.CREATE_DIARY_ENTRY.value(),
            "Create a family diary entry after user confirmation",
            CreateDiaryEntryInput.class,
            CreateDiaryEntryOutput.class,
            AgentToolSideEffect.WRITE,
            AgentToolConfirmationRequirement.REQUIRED,
            AgentToolPrivacyLevel.FAMILY_PRIVATE);

    private final AgentDiaryEntryFacade diaryEntryFacade;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<CreateDiaryEntryInput> inputType() {
        return CreateDiaryEntryInput.class;
    }

    @Override
    public CreateDiaryEntryOutput execute(AgentRunContext context, CreateDiaryEntryInput input) {
        if (input.content() == null || input.content().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Diary content is required");
        }
        CreateDiaryEntryRequest request = new CreateDiaryEntryRequest();
        request.setFamilyId(context.familyId());
        request.setContent(input.content());
        request.setEntryType(input.entryType());
        request.setTitle(input.title());
        request.setMood(input.mood());
        request.setTags(input.tags());
        request.setVisibility(input.visibility());
        if (input.metadata() != null) {
            request.setMetadata(DiaryEntryMetadata.fromMap(input.metadata().toMap()));
        }
        DiaryEntry entry = diaryEntryFacade.create(request);
        return new CreateDiaryEntryOutput(entry.getId());
    }
}
