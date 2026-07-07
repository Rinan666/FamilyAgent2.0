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
import com.familyagent.module.agent.harness.dto.CreateGrowthGuardRecordInput;
import com.familyagent.module.agent.harness.dto.CreateGrowthGuardRecordOutput;
import com.familyagent.module.growth.dto.CreateGrowthGuardRecordRequest;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.AgentGrowthGuardRecordFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CreateGrowthGuardRecordTool implements AgentTool<CreateGrowthGuardRecordInput, CreateGrowthGuardRecordOutput> {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            AgentToolName.CREATE_GROWTH_GUARD_RECORD.value(),
            "Create a growth guard observation after user confirmation",
            CreateGrowthGuardRecordInput.class,
            CreateGrowthGuardRecordOutput.class,
            AgentToolSideEffect.WRITE,
            AgentToolConfirmationRequirement.REQUIRED,
            AgentToolPrivacyLevel.FAMILY_PRIVATE);

    private final AgentGrowthGuardRecordFacade growthGuardRecordFacade;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<CreateGrowthGuardRecordInput> inputType() {
        return CreateGrowthGuardRecordInput.class;
    }

    @Override
    public CreateGrowthGuardRecordOutput execute(AgentRunContext context, CreateGrowthGuardRecordInput input) {
        if (input.targetUserId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Growth guard target user is required");
        }
        if (input.content() == null || input.content().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Growth guard content is required");
        }
        CreateGrowthGuardRecordRequest request = new CreateGrowthGuardRecordRequest();
        request.setFamilyId(context.familyId());
        request.setTargetUserId(input.targetUserId());
        request.setCategory(input.category());
        request.setContent(input.content());
        request.setSeverity(input.severity());
        request.setObservedAt(input.observedAt());
        request.setFollowUpAt(input.followUpAt());
        request.setVisibility(input.visibility());
        if (input.metadata() != null) {
            request.setMetadata(input.metadata().toMap());
        }
        GrowthGuardRecord record = growthGuardRecordFacade.create(request);
        return new CreateGrowthGuardRecordOutput(record.getId());
    }
}
