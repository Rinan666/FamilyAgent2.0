package com.familyagent.module.agent.service;

import com.familyagent.module.agent.constant.AgentSaveTool;
import com.familyagent.module.agent.dto.AgentSaveMemoryPlanRequest;
import com.familyagent.module.agent.dto.AgentSaveToolPlan;
import com.familyagent.module.skillrun.dto.CreateSkillRunRequest;
import com.familyagent.module.skillrun.dto.UpdateSkillRunRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentSaveMemoryPlanAssemblerTest {

    private final AgentSaveMemoryPlanAssembler assembler = new AgentSaveMemoryPlanAssembler();

    @Test
    void createRunUsesGenericAuditSummaryInsteadOfRawFamilyContent() {
        AgentSaveMemoryPlanRequest request = new AgentSaveMemoryPlanRequest();
        request.setFamilyId(10L);
        request.setMessage("孩子今天因为作业哭了很久，这是家庭原始内容");
        request.setSource("FAMILY_AGENT_CHAT");

        CreateSkillRunRequest run = assembler.createRunRequest(request, "request-1", 91L);

        assertEquals("Save-memory planning requested", run.getInputSummary());
        assertFalse(run.getInputSummary().contains("孩子"));
        assertEquals("request-1", run.getMetadata().getRequestId());
        assertEquals(91L, run.getMetadata().getAgentRunId());
        assertEquals("1.0.0", run.getMetadata().getSkillVersion());
        assertEquals("memory.save_plan.v1", run.getMetadata().getPromptVersion());
        assertEquals("save_tool_plan.schema.v1", run.getMetadata().getSchemaVersion());
    }

    @Test
    void completedRunWaitsWhenPlanRequiresPersistence() {
        AgentSaveToolPlan plan = new AgentSaveToolPlan();
        plan.setShouldSave(true);
        plan.setTool(AgentSaveTool.FAMILY_MEMORY);
        plan.setReason("包含可复用学习策略");

        UpdateSkillRunRequest update = assembler.completedRunUpdate(plan, "request-2", 92L);

        assertEquals("PLANNED", update.getStatus());
        assertEquals("FAMILY_MEMORY", update.getMetadata().getSavedRecordType());
        assertEquals("FAMILY_MEMORY", update.getMetadata().getPlannedTool());
    }

    @Test
    void completedRunFinishesWhenNothingShouldBeSaved() {
        AgentSaveToolPlan plan = new AgentSaveToolPlan();
        plan.setShouldSave(false);
        plan.setTool(AgentSaveTool.NONE);
        plan.setReason("内容缺乏持久价值");

        assertEquals("SUCCEEDED", assembler.completedRunUpdate(plan, "request-3", 93L).getStatus());
    }
}
