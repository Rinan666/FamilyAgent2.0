package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.dto.AgentChatStreamRequest;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentRunLifecycleService;
import com.familyagent.module.agent.harness.AgentTraceRecorder;
import com.familyagent.module.agent.harness.constant.AgentRunErrorCode;
import com.familyagent.module.agent.harness.dto.AgentTraceObservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentChatRunService {

    private final AgentRunLifecycleService runLifecycleService;
    private final AgentTraceRecorder traceRecorder;

    public AgentRunContext start(
            AgentChatStreamRequest request,
            Long userId,
            String requestId) {
        AgentRunContext context = new AgentRunContext(
                null,
                requestId,
                request.getFamilyId(),
                userId,
                null,
                request.getKnowledgePoint(),
                request.getSubject(),
                "chat_stream",
                false);
        return runLifecycleService.startOrResume(context);
    }

    public void complete(AgentRunContext context, AgentChatStreamTracker tracker) {
        recordTraceObservations(context, tracker.traceObservations());
        if (tracker.failed()) {
            String errorCode = tracker.errorCode();
            runLifecycleService.fail(
                    context,
                    errorCode == null ? AgentRunErrorCode.STREAM_UNAVAILABLE.code() : errorCode);
            return;
        }
        if (tracker.completedSuccessfully()) {
            runLifecycleService.succeed(context);
            return;
        }
        runLifecycleService.fail(context, AgentRunErrorCode.STREAM_EOF.code());
    }

    public void failPreparation(AgentRunContext context, RuntimeException error) {
        runLifecycleService.fail(context, preparationErrorCode(error));
    }

    public void failStream(AgentRunContext context) {
        runLifecycleService.fail(context, AgentRunErrorCode.STREAM_UNAVAILABLE.code());
    }

    public void failIo(AgentRunContext context) {
        runLifecycleService.fail(context, AgentRunErrorCode.IO_ERROR.code());
    }

    private String preparationErrorCode(RuntimeException error) {
        if (error instanceof BusinessException businessError) {
            if (businessError.getCode() == ErrorCode.RATE_LIMIT_EXCEEDED.getCode()) {
                return AgentRunErrorCode.RATE_LIMITED.code();
            }
            if (businessError.getCode() == ErrorCode.BAD_REQUEST.getCode()) {
                return AgentRunErrorCode.REQUEST_REJECTED.code();
            }
        }
        return AgentRunErrorCode.EXECUTION_FAILED.code();
    }

    private void recordTraceObservations(
            AgentRunContext context,
            java.util.List<AgentTraceObservation> observations) {
        for (AgentTraceObservation observation : observations) {
            try {
                traceRecorder.recordObservation(context, observation);
            } catch (RuntimeException error) {
                log.warn("Failed to record chat trace observation: agentRunId={}, operation={}",
                        context.runId(), observation.operation(), error);
            }
        }
    }
}
