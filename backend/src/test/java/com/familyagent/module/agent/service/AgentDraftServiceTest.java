package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.DraftGenerationClient;
import com.familyagent.infra.ai.dto.OrganizeDraftResponse;
import com.familyagent.module.agent.constant.AgentDraftSkillContract;
import com.familyagent.module.agent.dto.AgentDraftResult;
import com.familyagent.module.agent.dto.AgentOrganizeDraftRequest;
import com.familyagent.module.agent.dto.AgentOrganizedDraft;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.skillrun.entity.SkillRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentDraftServiceTest {

    @Mock private DraftGenerationClient draftGenerationClient;
    @Mock private AgentDraftRunService runService;

    private AgentDraftService draftService;

    @BeforeEach
    void setUp() {
        draftService = new AgentDraftService(
                draftGenerationClient,
                runService,
                new AgentDraftResponseGuard(),
                new AgentDraftFailureMapper(),
                new AgentDraftRequestIdFactory());
    }

    @Test
    void organizeCompletesObservableDraftRun() {
        AgentDraftRun run = run();
        when(runService.start(eq(AgentDraftSkillContract.ORGANIZE_DRAFT), eq(10L), eq(101L), eq("request-1")))
                .thenReturn(run);
        AgentOrganizedDraft draft = new AgentOrganizedDraft();
        draft.setTitle("Draft title");
        OrganizeDraftResponse response = new OrganizeDraftResponse();
        response.setSuccess(true);
        response.setData(draft);
        when(draftGenerationClient.organize(any(), eq("request-1"), eq(91L))).thenReturn(response);

        AgentDraftResult<AgentOrganizedDraft> result = draftService.organize(request(), 101L);

        assertEquals(77L, result.skillRunId());
        assertEquals(draft, result.data());
        verify(runService).succeed(run);
    }

    @Test
    void providerFailureIsRecordedWithStableErrorCode() {
        AgentDraftRun run = run();
        when(runService.start(eq(AgentDraftSkillContract.ORGANIZE_DRAFT), eq(10L), eq(101L), eq("request-1")))
                .thenReturn(run);
        OrganizeDraftResponse response = new OrganizeDraftResponse();
        response.setSuccess(false);
        response.setErrorCode("AI_PROVIDER_ERROR");
        when(draftGenerationClient.organize(any(), eq("request-1"), eq(91L))).thenReturn(response);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> draftService.organize(request(), 101L));

        assertEquals(ErrorCode.AI_SERVICE_ERROR.getCode(), error.getCode());
        verify(runService).fail(run, "AI_PROVIDER_ERROR");
    }

    private AgentOrganizeDraftRequest request() {
        AgentOrganizeDraftRequest request = new AgentOrganizeDraftRequest();
        request.setFamilyId(10L);
        request.setContent("A family event worth organizing");
        request.setRequestId("request-1");
        return request;
    }

    private AgentDraftRun run() {
        SkillRun skillRun = new SkillRun();
        skillRun.setId(77L);
        AgentRunContext context = new AgentRunContext(
                91L,
                "request-1",
                10L,
                101L,
                null,
                "family_draft",
                "FamilyAgent",
                "organize_draft",
                true);
        AgentRunStepRecord span = new AgentRunStepRecord();
        span.setId(501L);
        return new AgentDraftRun(AgentDraftSkillContract.ORGANIZE_DRAFT, context, span, skillRun);
    }
}
