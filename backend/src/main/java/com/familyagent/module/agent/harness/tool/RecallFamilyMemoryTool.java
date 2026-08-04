package com.familyagent.module.agent.harness.tool;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentTool;
import com.familyagent.module.agent.harness.AgentToolDescriptor;
import com.familyagent.module.agent.harness.AgentToolErrorMapper;
import com.familyagent.module.agent.harness.AgentTraceRecorder;
import com.familyagent.module.agent.harness.constant.AgentRunStepType;
import com.familyagent.module.agent.harness.constant.AgentTraceOperation;
import com.familyagent.module.agent.harness.constant.AgentTracePrivacyCategory;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolErrorCode;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.constant.AgentToolPrivacyLevel;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.dto.AgentTraceObservation;
import com.familyagent.module.agent.harness.dto.AgentTraceSpanDescriptor;
import com.familyagent.module.agent.harness.dto.RecallFamilyMemoryInput;
import com.familyagent.module.agent.harness.dto.RecallFamilyMemoryOutput;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.memory.facade.AgentMemoryContextFacade;
import com.familyagent.module.memory.facade.AgentMemoryContextResult;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
class RecallFamilyMemoryTool implements AgentTool<RecallFamilyMemoryInput, RecallFamilyMemoryOutput> {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            AgentToolName.RECALL_FAMILY_MEMORY.value(),
            "Recall authorized family memory context for a FamilyAgent request",
            RecallFamilyMemoryInput.class,
            RecallFamilyMemoryOutput.class,
            AgentToolSideEffect.READ_ONLY,
            AgentToolConfirmationRequirement.NOT_REQUIRED,
            AgentToolPrivacyLevel.FAMILY_PRIVATE);
    private static final AgentTraceSpanDescriptor TRACE_DESCRIPTOR = new AgentTraceSpanDescriptor(
            AgentRunStepType.MEMORY_RECALL,
            AgentTraceOperation.MEMORY_RECALL_AUTHORIZED.value(),
            null,
            null,
            null,
            null,
            null,
            List.of(AgentTracePrivacyCategory.FAMILY_DATA));

    private final AgentMemoryContextFacade memoryContextFacade;
    private final AgentTraceRecorder traceRecorder;
    private final AgentToolErrorMapper errorMapper;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<RecallFamilyMemoryInput> inputType() {
        return RecallFamilyMemoryInput.class;
    }

    @Override
    public RecallFamilyMemoryOutput execute(AgentRunContext context, RecallFamilyMemoryInput input) {
        AgentRunStepRecord span = startTrace(context);
        try {
            AgentMemoryContextResult result = memoryContextFacade.buildFamilyAgentContextResult(
                    context.familyId(),
                    context.viewerUserId(),
                    input.memberMessage(),
                    input.recentUserMessages(),
                    input.recallPlan());
            if (result.success()) {
                completeTrace(span);
            } else {
                failTrace(span, result.errorCode(), true);
            }
            recordEmbeddingTrace(context, result.embeddingObservation());
            return new RecallFamilyMemoryOutput(result.context(), result.metadata());
        } catch (BusinessException error) {
            failTrace(span, errorMapper.errorCode(error), false);
            throw error;
        } catch (RuntimeException error) {
            failTrace(span, AgentToolErrorCode.EXECUTION_FAILED.code(), false);
            throw error;
        }
    }

    private AgentRunStepRecord startTrace(AgentRunContext context) {
        try {
            return traceRecorder.start(context, TRACE_DESCRIPTOR);
        } catch (RuntimeException error) {
            log.warn("Failed to start memory recall trace: agentRunId={}, errorType={}",
                    context.runId(), error.getClass().getSimpleName());
            return null;
        }
    }

    private void completeTrace(AgentRunStepRecord span) {
        if (span == null) {
            return;
        }
        try {
            traceRecorder.succeed(span);
        } catch (RuntimeException error) {
            log.warn("Failed to complete memory recall trace: errorType={}",
                    error.getClass().getSimpleName());
        }
    }

    private void failTrace(AgentRunStepRecord span, String errorCode, boolean degraded) {
        if (span == null) {
            return;
        }
        try {
            if (degraded) {
                traceRecorder.failDegraded(span, errorCode);
                return;
            }
            traceRecorder.fail(span, errorCode);
        } catch (RuntimeException error) {
            log.warn("Failed to fail memory recall trace: errorCode={}, errorType={}",
                    errorCode, error.getClass().getSimpleName());
        }
    }

    private void recordEmbeddingTrace(
            AgentRunContext context,
            EmbeddingCallObservation observation) {
        if (observation == null || !observation.attempted()) {
            return;
        }
        try {
            traceRecorder.recordObservation(context, new AgentTraceObservation(
                    AgentRunStepType.EMBEDDING,
                    AgentTraceOperation.EMBEDDING_RECALL_QUERY.value(),
                    observation.provider(),
                    observation.model(),
                    null,
                    null,
                    observation.latencyMs(),
                    observation.success(),
                    observation.errorCode(),
                    observation.degraded(),
                    List.of(AgentTracePrivacyCategory.FAMILY_DATA)));
        } catch (RuntimeException error) {
            log.warn("Failed to record recall embedding trace: agentRunId={}, errorType={}",
                    context.runId(), error.getClass().getSimpleName());
        }
    }
}
