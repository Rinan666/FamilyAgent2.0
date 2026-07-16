package com.familyagent.module.agent.service;

import com.familyagent.infra.ai.DraftGenerationClient;
import com.familyagent.module.agent.constant.AgentDraftSkillContract;
import com.familyagent.module.agent.dto.AgentDraftResult;
import com.familyagent.module.agent.dto.AgentOrganizeDraftRequest;
import com.familyagent.module.agent.dto.AgentOrganizedDraft;
import com.familyagent.module.agent.dto.AgentPersonaMaterialDraft;
import com.familyagent.module.agent.dto.AgentPersonaMaterialDraftRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentDraftService {

    private final DraftGenerationClient draftGenerationClient;
    private final AgentDraftRunService runService;
    private final AgentDraftResponseGuard responseGuard;
    private final AgentDraftFailureMapper failureMapper;
    private final AgentDraftRequestIdFactory requestIdFactory;

    public AgentDraftResult<AgentOrganizedDraft> organize(
            AgentOrganizeDraftRequest request,
            Long viewerUserId) {
        String requestId = requestIdFactory.create("organize-draft", request.getRequestId());
        AgentDraftRun run = runService.start(
                AgentDraftSkillContract.ORGANIZE_DRAFT,
                request.getFamilyId(),
                viewerUserId,
                requestId);
        try {
            AgentOrganizedDraft draft = responseGuard.requireData(draftGenerationClient.organize(
                    request.toAiPayload(),
                    requestId,
                    run.context().runId()));
            runService.succeed(run);
            return new AgentDraftResult<>(run.skillRun().getId(), draft);
        } catch (RuntimeException error) {
            runService.fail(run, failureMapper.errorCode(error));
            throw failureMapper.toBusinessException(error);
        }
    }

    public AgentDraftResult<AgentPersonaMaterialDraft> organizePersonaMaterial(
            AgentPersonaMaterialDraftRequest request,
            Long viewerUserId) {
        String requestId = requestIdFactory.create("persona-material-draft", request.getRequestId());
        AgentDraftRun run = runService.start(
                AgentDraftSkillContract.PERSONA_MATERIAL_DRAFT,
                request.getFamilyId(),
                viewerUserId,
                requestId);
        try {
            AgentPersonaMaterialDraft draft = responseGuard.requireData(
                    draftGenerationClient.organizePersonaMaterial(
                            request.toAiPayload(),
                            requestId,
                            run.context().runId()));
            runService.succeed(run);
            return new AgentDraftResult<>(run.skillRun().getId(), draft);
        } catch (RuntimeException error) {
            runService.fail(run, failureMapper.errorCode(error));
            throw failureMapper.toBusinessException(error);
        }
    }
}
