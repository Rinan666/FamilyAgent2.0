package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.infra.ai.dto.SaveMemoryPlanResponse;
import com.familyagent.module.agent.constant.AgentSavePlanErrorCode;
import com.familyagent.module.agent.dto.AgentSaveMemoryPlanRequest;
import com.familyagent.module.agent.dto.AgentSaveMemoryPlanResult;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentRunLifecycleService;
import com.familyagent.module.agent.harness.AgentTraceRecorder;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.facade.AgentSkillRunFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSaveMemoryPlanService {

    private final AIServiceClient aiServiceClient;
    private final AgentSkillRunFacade skillRunFacade;
    private final AgentSaveMemoryPlanAssembler assembler;
    private final AgentRunLifecycleService runLifecycleService;
    private final AgentTraceRecorder traceRecorder;
    private final AgentSaveMemoryTraceFactory traceFactory;

    public AgentSaveMemoryPlanResult plan(AgentSaveMemoryPlanRequest request, Long viewerUserId) {
        String requestId = requestId(request.getRequestId());
        AgentRunContext runContext = runLifecycleService.startOrResume(new AgentRunContext(
                requestId,
                request.getFamilyId(),
                viewerUserId,
                null,
                "family_memory",
                "FamilyAgent",
                "save_memory_plan"));
        AgentRunStepRecord span = traceRecorder.start(runContext, traceFactory.create());
        SkillRun skillRun = null;
        try {
            skillRun = skillRunFacade.create(assembler.createRunRequest(
                    request,
                    requestId,
                    runContext.runId()));
            SaveMemoryPlanResponse response = aiServiceClient.planSaveMemory(
                    request.toAiPayload(),
                    requestId,
                    runContext.runId());
            if (response == null) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "保存规划服务暂时不可用");
            }
            if (!response.isSuccess()) {
                throw new SavePlanFailureException(response.getErrorCode());
            }
            if (response.getData() == null) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "保存规划服务返回无效结果");
            }
            skillRunFacade.update(skillRun.getId(), assembler.completedRunUpdate(
                    response.getData(),
                    requestId,
                    runContext.runId()));
            recordSuccess(runContext, span);
            return new AgentSaveMemoryPlanResult(skillRun.getId(), response.getData());
        } catch (RuntimeException error) {
            recordFailure(skillRun, runContext, span, requestId, errorCode(error));
            throw error;
        }
    }

    private void recordSuccess(AgentRunContext runContext, AgentRunStepRecord span) {
        try {
            traceRecorder.succeed(span);
            runLifecycleService.succeed(runContext);
        } catch (RuntimeException auditError) {
            log.warn("Failed to finalize successful save-plan trace: agentRunId={}", runContext.runId(), auditError);
        }
    }

    private void recordFailure(
            SkillRun skillRun,
            AgentRunContext runContext,
            AgentRunStepRecord span,
            String requestId,
            String errorCode) {
        try {
            traceRecorder.fail(span, errorCode);
            runLifecycleService.fail(runContext, errorCode);
        } catch (RuntimeException auditError) {
            log.warn("Failed to finalize failed save-plan trace: agentRunId={}", runContext.runId(), auditError);
        }
        if (skillRun == null) {
            return;
        }
        try {
            skillRunFacade.update(skillRun.getId(), assembler.failedRunUpdate(
                    requestId,
                    errorCode,
                    runContext.runId()));
        } catch (RuntimeException auditError) {
            log.warn("Failed to record save-plan failure: skillRunId={}", skillRun.getId(), auditError);
        }
    }

    private String requestId(String value) {
        if (value == null || value.isBlank()) {
            return "save-plan-" + UUID.randomUUID();
        }
        String text = value.trim();
        return text.length() <= 128 ? text : text.substring(0, 128);
    }

    private String errorCode(RuntimeException error) {
        if (error instanceof SavePlanFailureException planningError) {
            return planningError.errorCode().name();
        }
        if (error instanceof BusinessException businessError) {
            if (businessError.getCode() == ErrorCode.BAD_REQUEST.getCode()) {
                return AgentSavePlanErrorCode.AI_INPUT_REJECTED.name();
            }
            if (businessError.getCode() == ErrorCode.RATE_LIMIT_EXCEEDED.getCode()) {
                return AgentSavePlanErrorCode.AI_RATE_LIMITED.name();
            }
        }
        return AgentSavePlanErrorCode.AI_SERVICE_ERROR.name();
    }

    private static final class SavePlanFailureException extends BusinessException {

        private final AgentSavePlanErrorCode errorCode;

        private SavePlanFailureException(String errorCode) {
            super(ErrorCode.AI_SERVICE_ERROR, "保存规划服务暂时不可用");
            this.errorCode = AgentSavePlanErrorCode.fromExternal(errorCode);
        }

        private AgentSavePlanErrorCode errorCode() {
            return errorCode;
        }
    }
}
