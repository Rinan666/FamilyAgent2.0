package com.familyagent.module.agent.service;

import com.familyagent.module.agent.constant.AgentDraftSkillContract;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentRunLifecycleService;
import com.familyagent.module.agent.harness.AgentTraceRecorder;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.skillrun.dto.CreateSkillRunRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentDraftRunServiceTest {

    @Mock private FamilyMembershipFacade familyMembershipFacade;
    @Mock private AgentRunLifecycleService runLifecycleService;
    @Mock private AgentTraceRecorder traceRecorder;
    @Mock private AgentSkillRunFacade skillRunFacade;

    private AgentDraftRunService runService;

    @BeforeEach
    void setUp() {
        runService = new AgentDraftRunService(
                familyMembershipFacade,
                runLifecycleService,
                traceRecorder,
                skillRunFacade,
                new AgentDraftRunAssembler(),
                new AgentDraftTraceFactory());
    }

    @Test
    void startChecksMembershipAndRecordsArtifactVersions() {
        AgentRunContext context = context();
        when(runLifecycleService.startOrResume(any())).thenReturn(context);
        AgentRunStepRecord span = new AgentRunStepRecord();
        span.setId(501L);
        when(traceRecorder.start(eq(context), any())).thenReturn(span);
        SkillRun skillRun = new SkillRun();
        skillRun.setId(77L);
        when(skillRunFacade.create(any())).thenReturn(skillRun);

        AgentDraftRun run = runService.start(
                AgentDraftSkillContract.ORGANIZE_DRAFT,
                10L,
                101L,
                "request-1");

        assertEquals(77L, run.skillRun().getId());
        verify(familyMembershipFacade).checkMembership(10L);
        ArgumentCaptor<CreateSkillRunRequest> captor = ArgumentCaptor.forClass(CreateSkillRunRequest.class);
        verify(skillRunFacade).create(captor.capture());
        assertEquals("organize_draft", captor.getValue().getSkillName());
        assertEquals("memory.organize_draft.v1", captor.getValue().getMetadata().getPromptVersion());
        assertEquals("organized_draft.schema.v1", captor.getValue().getMetadata().getSchemaVersion());
        assertEquals(91L, captor.getValue().getMetadata().getAgentRunId());
    }

    @Test
    void failClosesSkillRunTraceAndAgentRun() {
        AgentRunContext context = context();
        AgentRunStepRecord span = new AgentRunStepRecord();
        span.setId(501L);
        SkillRun skillRun = new SkillRun();
        skillRun.setId(77L);
        AgentDraftRun run = new AgentDraftRun(
                AgentDraftSkillContract.ORGANIZE_DRAFT,
                context,
                span,
                skillRun);

        runService.fail(run, "AI_TIMEOUT");

        ArgumentCaptor<UpdateSkillRunRequest> captor = ArgumentCaptor.forClass(UpdateSkillRunRequest.class);
        verify(skillRunFacade).update(eq(77L), captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
        assertEquals("AI_TIMEOUT", captor.getValue().getMetadata().getExecutionErrorCode());
        verify(traceRecorder).fail(span, "AI_TIMEOUT");
        verify(runLifecycleService).fail(context, "AI_TIMEOUT");
    }

    private AgentRunContext context() {
        return new AgentRunContext(
                91L,
                "request-1",
                10L,
                101L,
                null,
                "family_draft",
                "FamilyAgent",
                "organize_draft",
                true);
    }
}
