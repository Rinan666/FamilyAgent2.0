package com.familyagent.module.agent.harness.tool;

import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentToolErrorMapper;
import com.familyagent.module.agent.harness.AgentTraceRecorder;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.constant.AgentRunStepType;
import com.familyagent.module.agent.harness.constant.AgentTraceOperation;
import com.familyagent.module.agent.harness.dto.AgentTraceObservation;
import com.familyagent.module.agent.harness.dto.AgentTraceSpanDescriptor;
import com.familyagent.module.agent.harness.dto.RecallFamilyMemoryInput;
import com.familyagent.module.agent.harness.dto.RecallFamilyMemoryOutput;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import com.familyagent.module.memory.facade.AgentMemoryContextFacade;
import com.familyagent.module.memory.facade.AgentMemoryContextErrorCode;
import com.familyagent.module.memory.facade.AgentMemoryContextResult;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.dto.MemoryRecallContextMetadata;
import com.familyagent.module.memory.dto.MemoryRecallRagMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecallFamilyMemoryToolTest {

    private final AgentMemoryContextFacade memoryContextFacade = mock(AgentMemoryContextFacade.class);
    private final AgentTraceRecorder traceRecorder = mock(AgentTraceRecorder.class);
    private final AgentToolErrorMapper errorMapper = mock(AgentToolErrorMapper.class);
    private final RecallFamilyMemoryTool tool = new RecallFamilyMemoryTool(
            memoryContextFacade,
            traceRecorder,
            errorMapper);

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
        AgentRunStepRecord span = new AgentRunStepRecord();
        span.setId(501L);
        when(traceRecorder.start(any(), any())).thenReturn(span);
        when(memoryContextFacade.buildFamilyAgentContextResult(
                10L,
                101L,
                "How should I talk about bedtime?",
                List.of("earlier turn")))
                .thenReturn(new com.familyagent.module.memory.facade.AgentMemoryContextResult(
                        "family_memory_hits:\n1. bedtime",
                        metadataWithMemoryCount(1)));

        RecallFamilyMemoryOutput output = tool.execute(context, input);

        assertEquals("family_memory_hits:\n1. bedtime", output.context());
        assertEquals(1, output.metadata().rag().memoryCount());
        assertEquals(AgentToolName.RECALL_FAMILY_MEMORY.value(), tool.descriptor().name());
        assertEquals(AgentToolSideEffect.READ_ONLY, tool.descriptor().sideEffect());
        assertEquals(AgentToolConfirmationRequirement.NOT_REQUIRED, tool.descriptor().confirmationRequirement());
        verify(memoryContextFacade).buildFamilyAgentContextResult(
                10L,
                101L,
                "How should I talk about bedtime?",
                List.of("earlier turn"));
        ArgumentCaptor<AgentTraceSpanDescriptor> traceCaptor = ArgumentCaptor.forClass(AgentTraceSpanDescriptor.class);
        verify(traceRecorder).start(org.mockito.ArgumentMatchers.eq(context), traceCaptor.capture());
        assertEquals(AgentRunStepType.MEMORY_RECALL, traceCaptor.getValue().stepType());
        assertEquals("memory.recall.authorized", traceCaptor.getValue().operation());
        verify(traceRecorder).succeed(span);
    }

    @Test
    void execute_recordsDegradedRecallFailureWithoutBreakingChatFallback() {
        AgentRunContext context = context();
        RecallFamilyMemoryInput input = new RecallFamilyMemoryInput("hello", List.of());
        AgentRunStepRecord span = new AgentRunStepRecord();
        span.setId(501L);
        when(traceRecorder.start(any(), any())).thenReturn(span);
        when(memoryContextFacade.buildFamilyAgentContextResult(10L, 101L, "hello", List.of()))
                .thenReturn(AgentMemoryContextResult.failed(AgentMemoryContextErrorCode.RECALL_FAILED));

        RecallFamilyMemoryOutput output = tool.execute(context, input);

        assertEquals("", output.context());
        assertEquals(null, output.metadata().retrievalQuery());
        verify(traceRecorder).failDegraded(span, AgentMemoryContextErrorCode.RECALL_FAILED.code());
        verify(traceRecorder, never()).succeed(span);
    }

    @Test
    void execute_continuesWhenTraceStorageIsUnavailable() {
        AgentRunContext context = context();
        RecallFamilyMemoryInput input = new RecallFamilyMemoryInput("hello", List.of());
        when(traceRecorder.start(any(), any())).thenThrow(new RuntimeException("trace database unavailable"));
        when(memoryContextFacade.buildFamilyAgentContextResult(10L, 101L, "hello", List.of()))
                .thenReturn(AgentMemoryContextResult.empty());

        RecallFamilyMemoryOutput output = tool.execute(context, input);

        assertEquals("", output.context());
        verify(traceRecorder, never()).succeed(any());
        verify(traceRecorder, never()).fail(any(), any());
        verify(traceRecorder, never()).failDegraded(any(), any());
    }

    @Test
    void execute_recordsTypedRecallQueryEmbeddingObservation() {
        AgentRunContext context = context();
        RecallFamilyMemoryInput input = new RecallFamilyMemoryInput("hello", List.of());
        AgentRunStepRecord span = new AgentRunStepRecord();
        span.setId(501L);
        EmbeddingCallObservation embeddingObservation = new EmbeddingCallObservation(
                true,
                false,
                true,
                "dashscope",
                "dashscope/text-embedding-v4",
                2,
                21L,
                "AI_EMBEDDING_DIMENSION_MISMATCH");
        when(traceRecorder.start(any(), any())).thenReturn(span);
        when(memoryContextFacade.buildFamilyAgentContextResult(10L, 101L, "hello", List.of()))
                .thenReturn(new AgentMemoryContextResult(
                        "",
                        MemoryRecallContextMetadata.empty(),
                        true,
                        null,
                        embeddingObservation));

        tool.execute(context, input);

        ArgumentCaptor<AgentTraceObservation> captor = ArgumentCaptor.forClass(AgentTraceObservation.class);
        verify(traceRecorder).recordObservation(org.mockito.ArgumentMatchers.eq(context), captor.capture());
        AgentTraceObservation trace = captor.getValue();
        assertEquals(AgentRunStepType.EMBEDDING, trace.stepType());
        assertEquals(AgentTraceOperation.EMBEDDING_RECALL_QUERY.value(), trace.operation());
        assertEquals("dashscope", trace.provider());
        assertEquals("dashscope/text-embedding-v4", trace.model());
        assertEquals(21L, trace.latencyMs());
        assertFalse(trace.success());
        assertTrue(trace.degraded());
        assertEquals("AI_EMBEDDING_DIMENSION_MISMATCH", trace.errorCode());
    }

    private AgentRunContext context() {
        return new AgentRunContext(
                500L,
                "req-1",
                10L,
                101L,
                null,
                "family_memory",
                "FamilyAgent",
                "chat_stream",
                false);
    }

    private static MemoryRecallContextMetadata metadataWithMemoryCount(int memoryCount) {
        return new MemoryRecallContextMetadata(
                new MemoryRecallRagMetadata(null, 0, 0, memoryCount, 0, 0, 0, memoryCount, List.of()),
                null);
    }
}
