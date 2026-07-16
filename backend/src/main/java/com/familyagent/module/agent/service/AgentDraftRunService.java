package com.familyagent.module.agent.service;

import com.familyagent.module.agent.constant.AgentDraftSkillContract;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentRunLifecycleService;
import com.familyagent.module.agent.harness.AgentTraceRecorder;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.facade.AgentSkillRunFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDraftRunService {

    private final FamilyMembershipFacade familyMembershipFacade;
    private final AgentRunLifecycleService runLifecycleService;
    private final AgentTraceRecorder traceRecorder;
    private final AgentSkillRunFacade skillRunFacade;
    private final AgentDraftRunAssembler assembler;
    private final AgentDraftTraceFactory traceFactory;

    public AgentDraftRun start(
            AgentDraftSkillContract contract,
            Long familyId,
            Long viewerUserId,
            String requestId) {
        familyMembershipFacade.checkMembership(familyId);
        AgentRunContext context = runLifecycleService.startOrResume(new AgentRunContext(
                requestId,
                familyId,
                viewerUserId,
                null,
                AgentDraftSkillContract.AGENT_MODE,
                AgentDraftSkillContract.SUBJECT,
                contract.getContextLabel()));
        AgentRunStepRecord span = traceRecorder.start(context, traceFactory.create(contract));
        try {
            SkillRun skillRun = skillRunFacade.create(assembler.create(
                    contract,
                    familyId,
                    requestId,
                    context.runId()));
            return new AgentDraftRun(contract, context, span, skillRun);
        } catch (RuntimeException error) {
            recordTraceFailure(context, span, "SKILL_RUN_CREATE_FAILED");
            throw error;
        }
    }

    public void succeed(AgentDraftRun run) {
        skillRunFacade.update(run.skillRun().getId(), assembler.completed(
                run.contract(),
                run.context().requestId(),
                run.context().runId()));
        try {
            traceRecorder.succeed(run.span());
        } catch (RuntimeException error) {
            log.warn("Failed to finalize successful draft trace: agentRunId={}, errorType={}",
                    run.context().runId(), error.getClass().getSimpleName());
        }
        try {
            runLifecycleService.succeed(run.context());
        } catch (RuntimeException error) {
            log.warn("Failed to finalize successful draft run: agentRunId={}, errorType={}",
                    run.context().runId(), error.getClass().getSimpleName());
        }
    }

    public void fail(AgentDraftRun run, String errorCode) {
        try {
            skillRunFacade.update(run.skillRun().getId(), assembler.failed(
                    run.contract(),
                    run.context().requestId(),
                    run.context().runId(),
                    errorCode));
        } catch (RuntimeException error) {
            log.warn("Failed to record draft SkillRun failure: skillRunId={}, errorType={}",
                    run.skillRun().getId(), error.getClass().getSimpleName());
        }
        recordTraceFailure(run.context(), run.span(), errorCode);
    }

    private void recordTraceFailure(
            AgentRunContext context,
            AgentRunStepRecord span,
            String errorCode) {
        try {
            traceRecorder.fail(span, errorCode);
        } catch (RuntimeException error) {
            log.warn("Failed to finalize failed draft trace: agentRunId={}, errorType={}",
                    context.runId(), error.getClass().getSimpleName());
        }
        try {
            runLifecycleService.fail(context, errorCode);
        } catch (RuntimeException error) {
            log.warn("Failed to finalize failed draft run: agentRunId={}, errorType={}",
                    context.runId(), error.getClass().getSimpleName());
        }
    }
}
