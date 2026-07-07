package com.familyagent.module.agent.harness.tool;

import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.dto.RecallFamilyMemoryInput;
import com.familyagent.module.agent.harness.dto.RecallFamilyMemoryOutput;
import com.familyagent.module.memory.facade.AgentMemoryContextFacade;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecallFamilyMemoryToolTest {

    private final AgentMemoryContextFacade memoryContextFacade = mock(AgentMemoryContextFacade.class);
    private final RecallFamilyMemoryTool tool = new RecallFamilyMemoryTool(memoryContextFacade);

    @Test
    void execute_usesMemoryContextFacadeWithViewerAndFamily() {
        AgentRunContext context = new AgentRunContext(
                "req-1",
                10L,
                101L,
                null,
                "family_memory",
                "family",
                "test");
        RecallFamilyMemoryInput input = new RecallFamilyMemoryInput(
                "How should I talk about bedtime?",
                List.of("earlier turn"));
        when(memoryContextFacade.buildFamilyAgentContextResult(
                10L,
                101L,
                "How should I talk about bedtime?",
                List.of("earlier turn")))
                .thenReturn(new com.familyagent.module.memory.facade.AgentMemoryContextResult(
                        "family_memory_hits:\n1. bedtime",
                        Map.of("rag", Map.of("memoryCount", 1))));

        RecallFamilyMemoryOutput output = tool.execute(context, input);

        assertEquals("family_memory_hits:\n1. bedtime", output.context());
        assertEquals(Map.of("rag", Map.of("memoryCount", 1)), output.metadata());
        assertEquals(AgentToolName.RECALL_FAMILY_MEMORY.value(), tool.descriptor().name());
        assertEquals(AgentToolSideEffect.READ_ONLY, tool.descriptor().sideEffect());
        assertEquals(AgentToolConfirmationRequirement.NOT_REQUIRED, tool.descriptor().confirmationRequirement());
        verify(memoryContextFacade).buildFamilyAgentContextResult(
                10L,
                101L,
                "How should I talk about bedtime?",
                List.of("earlier turn"));
    }
}
