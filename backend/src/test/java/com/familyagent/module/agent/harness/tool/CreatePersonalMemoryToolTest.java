package com.familyagent.module.agent.harness.tool;

import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.dto.CreatePersonalMemoryInput;
import com.familyagent.module.agent.harness.dto.CreatePersonalMemoryOutput;
import com.familyagent.module.memory.dto.CreatePersonalMemoryRequest;
import com.familyagent.module.memory.dto.PersonalMemoryView;
import com.familyagent.module.memory.facade.AgentPersonalMemoryFacade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreatePersonalMemoryToolTest {

    private final AgentPersonalMemoryFacade facade = mock(AgentPersonalMemoryFacade.class);
    private final CreatePersonalMemoryTool tool = new CreatePersonalMemoryTool(facade);

    @Test
    void execute_preservesVisibilityAndSelectedFamilies() {
        when(facade.create(any())).thenReturn(new PersonalMemoryView(
                88L, 101L, "PERSONAL", "KNOWLEDGE", "SELECTED_FAMILIES_VISIBLE",
                "A useful idea", "Useful idea", 3, BigDecimal.ONE, "ACTIVE", null,
                List.of(10L), LocalDateTime.now(), LocalDateTime.now()));
        CreatePersonalMemoryInput input = new CreatePersonalMemoryInput(
                "A useful idea", "KNOWLEDGE", "SELECTED_FAMILIES_VISIBLE",
                "Useful idea", 3, List.of(10L), null);

        CreatePersonalMemoryOutput output = tool.execute(
                new AgentRunContext("req", 10L, 101L, null, "family", "FamilyAgent", "save"),
                input);

        ArgumentCaptor<CreatePersonalMemoryRequest> captor = ArgumentCaptor.forClass(CreatePersonalMemoryRequest.class);
        verify(facade).create(captor.capture());
        assertEquals(List.of(10L), captor.getValue().getSelectedFamilyIds());
        assertEquals("SELECTED_FAMILIES_VISIBLE", captor.getValue().getVisibility());
        assertEquals(AgentToolName.CREATE_PERSONAL_MEMORY.value(), tool.descriptor().name());
        assertEquals(AgentToolConfirmationRequirement.REQUIRED, tool.descriptor().confirmationRequirement());
        assertEquals(88L, output.memoryEntryId());
    }
}
