package com.familyagent.module.agent.harness.tool;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.dto.AgentSaveMemoryMetadata;
import com.familyagent.module.agent.harness.dto.CreateFamilyMemoryInput;
import com.familyagent.module.agent.harness.dto.CreateFamilyMemoryOutput;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.facade.AgentFamilyMemoryFacade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateFamilyMemoryToolTest {

    private final AgentFamilyMemoryFacade familyMemoryFacade = mock(AgentFamilyMemoryFacade.class);
    private final CreateFamilyMemoryTool tool = new CreateFamilyMemoryTool(familyMemoryFacade);

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
        assertEquals(AgentToolName.CREATE_FAMILY_MEMORY.value(), tool.descriptor().name());
        assertEquals(AgentToolSideEffect.WRITE, tool.descriptor().sideEffect());
        assertEquals(AgentToolConfirmationRequirement.REQUIRED, tool.descriptor().confirmationRequirement());
    }

    @Test
    void execute_usesContextFamilyAndMemoryFacade() {
        CreateFamilyMemoryInput input = new CreateFamilyMemoryInput(
                "Grandpa taught us to repair things before replacing them.",
                "EXPERIENCE",
                "FAMILY_VISIBLE",
                "Repair before replacing.",
                4,
                22L,
                java.util.List.of("story"),
                metadata());
        MemoryEntry entry = new MemoryEntry();
        entry.setId(99L);
        when(familyMemoryFacade.create(org.mockito.ArgumentMatchers.any(CreateFamilyMemoryRequest.class)))
                .thenReturn(entry);

        CreateFamilyMemoryOutput output = tool.execute(context, input);

        ArgumentCaptor<CreateFamilyMemoryRequest> captor = ArgumentCaptor.forClass(CreateFamilyMemoryRequest.class);
        verify(familyMemoryFacade).create(captor.capture());
        CreateFamilyMemoryRequest request = captor.getValue();
        assertEquals(10L, request.getFamilyId());
        assertEquals("Grandpa taught us to repair things before replacing them.", request.getContent());
        assertEquals("EXPERIENCE", request.getType());
        assertEquals("FAMILY_VISIBLE", request.getScope());
        assertEquals("Repair before replacing.", request.getSummary());
        assertEquals(4, request.getImportance());
        assertEquals(22L, request.getRelatedUserId());
        assertEquals(java.util.List.of("story"), request.getTags());
        assertEquals("MIRROR_AGENT_TOOL", request.getMetadata().getSource());
        assertEquals("FAMILY_MEMORY", request.getMetadata().toMap().get("plannedTool"));
        assertEquals("FAMILY_EXPERIENCE", request.getMetadata().toMap().get("sourceType"));
        assertEquals(99L, output.memoryEntryId());
    }

    @Test
    void execute_blankContent_rejectsBeforeFacadeCall() {
        CreateFamilyMemoryInput input = new CreateFamilyMemoryInput(
                " ",
                "FAMILY_STORY",
                "FAMILY_VISIBLE",
                null,
                null,
                null,
                java.util.List.of(),
                metadata());

        assertThrows(BusinessException.class, () -> tool.execute(context, input));
    }

    private static AgentSaveMemoryMetadata metadata() {
        AgentSaveMemoryMetadata metadata = new AgentSaveMemoryMetadata();
        metadata.setSource("MIRROR_AGENT_TOOL");
        metadata.setPlannedTool("FAMILY_MEMORY");
        metadata.setSourceType("FAMILY_EXPERIENCE");
        metadata.setScenario("Mirror save");
        metadata.setTarget("Child");
        return metadata;
    }
}
