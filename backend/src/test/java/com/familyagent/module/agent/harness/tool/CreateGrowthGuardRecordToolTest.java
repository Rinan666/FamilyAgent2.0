package com.familyagent.module.agent.harness.tool;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.dto.AgentSaveMemoryMetadata;
import com.familyagent.module.agent.harness.dto.CreateGrowthGuardRecordInput;
import com.familyagent.module.agent.harness.dto.CreateGrowthGuardRecordOutput;
import com.familyagent.module.growth.dto.CreateGrowthGuardRecordRequest;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.AgentGrowthGuardRecordFacade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateGrowthGuardRecordToolTest {

    private final AgentGrowthGuardRecordFacade growthGuardRecordFacade = mock(AgentGrowthGuardRecordFacade.class);
    private final CreateGrowthGuardRecordTool tool = new CreateGrowthGuardRecordTool(growthGuardRecordFacade);

    private final AgentRunContext context = new AgentRunContext(
            "req-1",
            10L,
            101L,
            null,
            "family_memory",
            "family",
            "test");

    @Test
    void descriptor_requiresConfirmationForWrite() {
        assertEquals(AgentToolName.CREATE_GROWTH_GUARD_RECORD.value(), tool.descriptor().name());
        assertEquals(AgentToolSideEffect.WRITE, tool.descriptor().sideEffect());
        assertEquals(AgentToolConfirmationRequirement.REQUIRED, tool.descriptor().confirmationRequirement());
    }

    @Test
    void execute_usesContextFamilyAndGrowthFacade() {
        LocalDate observedAt = LocalDate.of(2026, 7, 1);
        LocalDate followUpAt = LocalDate.of(2026, 7, 8);
        CreateGrowthGuardRecordInput input = new CreateGrowthGuardRecordInput(
                202L,
                "SLEEP",
                "The child had trouble falling asleep after late screen time.",
                4,
                observedAt,
                followUpAt,
                "CARE_VISIBLE",
                metadata());
        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setId(77L);
        when(growthGuardRecordFacade.create(org.mockito.ArgumentMatchers.any(CreateGrowthGuardRecordRequest.class)))
                .thenReturn(record);

        CreateGrowthGuardRecordOutput output = tool.execute(context, input);

        ArgumentCaptor<CreateGrowthGuardRecordRequest> captor =
                ArgumentCaptor.forClass(CreateGrowthGuardRecordRequest.class);
        verify(growthGuardRecordFacade).create(captor.capture());
        CreateGrowthGuardRecordRequest request = captor.getValue();
        assertEquals(10L, request.getFamilyId());
        assertEquals(202L, request.getTargetUserId());
        assertEquals("SLEEP", request.getCategory());
        assertEquals("The child had trouble falling asleep after late screen time.", request.getContent());
        assertEquals(4, request.getSeverity());
        assertEquals(observedAt, request.getObservedAt());
        assertEquals(followUpAt, request.getFollowUpAt());
        assertEquals("CARE_VISIBLE", request.getVisibility());
        assertEquals("MIRROR_AGENT_TOOL", request.getMetadata().get("source"));
        assertEquals("PENDING", request.getMetadata().get("followUpStatus"));
        assertEquals(202L, request.getMetadata().get("relatedUserId"));
        assertEquals(77L, output.growthGuardRecordId());
    }

    @Test
    void execute_missingTargetRejectsBeforeFacadeCall() {
        CreateGrowthGuardRecordInput input = new CreateGrowthGuardRecordInput(
                null,
                "SLEEP",
                "Late screen time affected sleep.",
                3,
                null,
                null,
                "CARE_VISIBLE",
                metadata());

        assertThrows(BusinessException.class, () -> tool.execute(context, input));
    }

    @Test
    void execute_blankContentRejectsBeforeFacadeCall() {
        CreateGrowthGuardRecordInput input = new CreateGrowthGuardRecordInput(
                202L,
                "SLEEP",
                " ",
                3,
                null,
                null,
                "CARE_VISIBLE",
                metadata());

        assertThrows(BusinessException.class, () -> tool.execute(context, input));
    }

    private static AgentSaveMemoryMetadata metadata() {
        AgentSaveMemoryMetadata metadata = new AgentSaveMemoryMetadata();
        metadata.setSource("MIRROR_AGENT_TOOL");
        metadata.setPlannedTool("GROWTH_GUARD");
        metadata.setRelatedUserId(202L);
        metadata.setSourceType("GROWTH_OBSERVATION");
        metadata.setFollowUpStatus("PENDING");
        return metadata;
    }
}
