package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.infra.ai.dto.SaveMemoryPlanResponse;
import com.familyagent.module.agent.constant.AgentSaveTool;
import com.familyagent.module.agent.dto.AgentSaveMemoryPlanRequest;
import com.familyagent.module.agent.dto.AgentSaveMemoryPlanResult;
import com.familyagent.module.agent.dto.AgentSaveToolPlan;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentRunLifecycleService;
import com.familyagent.module.agent.harness.AgentTraceRecorder;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.skillrun.dto.UpdateSkillRunRequest;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.facade.AgentSkillRunFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSaveMemoryPlanServiceTest {

    @Mock private AIServiceClient aiServiceClient;
    @Mock private AgentSkillRunFacade skillRunFacade;
    @Mock private AgentRunLifecycleService runLifecycleService;
    @Mock private AgentTraceRecorder traceRecorder;

    private AgentSaveMemoryPlanService planService;

    @BeforeEach
    void setUp() {
        planService = new AgentSaveMemoryPlanService(
                aiServiceClient,
                skillRunFacade,
                new AgentSaveMemoryPlanAssembler(),
                runLifecycleService,
                traceRecorder,
                new AgentSaveMemoryTraceFactory());
    }

    @Test
    void planCreatesAndCompletesStronglyTypedSkillRun() {
        SkillRun run = new SkillRun();
        run.setId(77L);
        when(skillRunFacade.create(any())).thenReturn(run);
        AgentSaveToolPlan plan = new AgentSaveToolPlan();
        plan.setShouldSave(true);
        plan.setTool(AgentSaveTool.DIARY);
        plan.setReason("包含具体个人经历");
        SaveMemoryPlanResponse response = new SaveMemoryPlanResponse();
        response.setSuccess(true);
        response.setData(plan);
        prepareRun("request-1");
        when(aiServiceClient.planSaveMemory(any(), eq("request-1"), eq(91L))).thenReturn(response);

        AgentSaveMemoryPlanResult result = planService.plan(request("request-1"), 101L);

        assertEquals(77L, result.skillRunId());
        assertEquals(plan, result.plan());
        ArgumentCaptor<UpdateSkillRunRequest> updateCaptor = ArgumentCaptor.forClass(UpdateSkillRunRequest.class);
        verify(skillRunFacade).update(eq(77L), updateCaptor.capture());
        assertEquals("PLANNED", updateCaptor.getValue().getStatus());
        assertEquals("DIARY_ENTRY", updateCaptor.getValue().getMetadata().getSavedRecordType());
        assertEquals(91L, updateCaptor.getValue().getMetadata().getAgentRunId());
        verify(traceRecorder).succeed(any(AgentRunStepRecord.class));
        verify(runLifecycleService).succeed(any(AgentRunContext.class));
    }

    @Test
    void planMarksRunFailedWhenAiInputIsRejected() {
        SkillRun run = new SkillRun();
        run.setId(78L);
        when(skillRunFacade.create(any())).thenReturn(run);
        prepareRun("request-2");
        when(aiServiceClient.planSaveMemory(any(), eq("request-2"), eq(91L)))
                .thenThrow(new BusinessException(ErrorCode.BAD_REQUEST, "内容疑似低俗暗语"));

        assertThrows(BusinessException.class, () -> planService.plan(request("request-2"), 101L));

        ArgumentCaptor<UpdateSkillRunRequest> updateCaptor = ArgumentCaptor.forClass(UpdateSkillRunRequest.class);
        verify(skillRunFacade).update(eq(78L), updateCaptor.capture());
        assertEquals("FAILED", updateCaptor.getValue().getStatus());
        assertEquals("AI_INPUT_REJECTED", updateCaptor.getValue().getMetadata().getExecutionErrorCode());
        verify(traceRecorder).fail(any(AgentRunStepRecord.class), eq("AI_INPUT_REJECTED"));
    }

    @Test
    void planPreservesStructuredProviderFailureInSkillRun() {
        SkillRun run = new SkillRun();
        run.setId(79L);
        when(skillRunFacade.create(any())).thenReturn(run);
        SaveMemoryPlanResponse response = SaveMemoryPlanResponse.unavailable();
        response.setErrorCode("AI_PROVIDER_ERROR");
        prepareRun("request-3");
        when(aiServiceClient.planSaveMemory(any(), eq("request-3"), eq(91L))).thenReturn(response);

        assertThrows(BusinessException.class, () -> planService.plan(request("request-3"), 101L));

        ArgumentCaptor<UpdateSkillRunRequest> updateCaptor = ArgumentCaptor.forClass(UpdateSkillRunRequest.class);
        verify(skillRunFacade).update(eq(79L), updateCaptor.capture());
        assertEquals("FAILED", updateCaptor.getValue().getStatus());
        assertEquals("AI_PROVIDER_ERROR", updateCaptor.getValue().getMetadata().getExecutionErrorCode());
    }

    private void prepareRun(String requestId) {
        AgentRunContext context = new AgentRunContext(
                91L,
                requestId,
                10L,
                101L,
                null,
                "family_memory",
                "FamilyAgent",
                "save_memory_plan",
                true);
        when(runLifecycleService.startOrResume(any())).thenReturn(context);
        AgentRunStepRecord span = new AgentRunStepRecord();
        span.setId(501L);
        when(traceRecorder.start(eq(context), any())).thenReturn(span);
    }

    private AgentSaveMemoryPlanRequest request(String requestId) {
        AgentSaveMemoryPlanRequest request = new AgentSaveMemoryPlanRequest();
        request.setFamilyId(10L);
        request.setMessage("今天发生了一件值得记录的事");
        request.setSource("FAMILY_AGENT_CHAT");
        request.setRequestId(requestId);
        return request;
    }
}
