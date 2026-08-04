package com.familyagent.module.memory.facade;

import com.familyagent.common.constant.MemoryRecallSourceType;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.dto.MemoryRecallPlan;
import com.familyagent.module.memory.dto.RecallParticipantSummary;
import com.familyagent.module.memory.dto.RecallSourceSummary;
import com.familyagent.module.memory.dto.UnifiedAuthorizedMemoryRecallResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.service.UnifiedAuthorizedMemoryRecallService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMemoryContextFacadeTest {

    private final UnifiedAuthorizedMemoryRecallService recallService = mock(UnifiedAuthorizedMemoryRecallService.class);
    private final AgentMemoryContextFacade facade = new AgentMemoryContextFacade(
            recallService,
            new AgentUnifiedMemoryContextFormatter());

    @Test
    void buildFamilyAgentContext_usesUnifiedAuthorizedRecall() {
        MemoryEntry memory = entry();
        AuthorizedMemoryRecallCandidate candidate = new AuthorizedMemoryRecallCandidate(
                memory,
                MemoryRecallSourceType.FAMILY_EXPERIENCE,
                null);
        RecallSourceSummary source = RecallSourceSummary.builder()
                .id(candidate.publicId())
                .sourceType(MemoryRecallSourceType.FAMILY_EXPERIENCE.name())
                .author(new RecallParticipantSummary(202L, "张三", "二叔", null, false, false))
                .build();
        when(recallService.recall(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UnifiedAuthorizedMemoryRecallResult(
                        List.of(candidate),
                        List.of(source),
                        "UNIFIED_TEXT_FALLBACK",
                        List.of("older turn earlier question how should we talk about teeth"),
                        3,
                        null));

        String context = facade.buildFamilyAgentContext(
                10L,
                101L,
                "how should we talk about teeth",
                List.of("older turn", "earlier question"));

        ArgumentCaptor<MemoryRecallPlan> captor = ArgumentCaptor.forClass(MemoryRecallPlan.class);
        verify(recallService).recall(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.isNull(),
                captor.capture());
        assertEquals(8, captor.getValue().resultLimit());
        assertTrue(context.contains("authorized_record_hits"));
        assertTrue(context.contains("Brush teeth before sleep"));
    }

    @Test
    void buildFamilyAgentContextResult_exposesRecallFailureWithoutSensitiveDetails() {
        when(recallService.recall(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("database connection secret"));

        AgentMemoryContextResult result = facade.buildFamilyAgentContextResult(
                10L,
                101L,
                "hello",
                List.of());

        assertFalse(result.success());
        assertEquals(AgentMemoryContextErrorCode.RECALL_FAILED.code(), result.errorCode());
        assertEquals("", result.context());
        assertTrue(result.metadata().isEmpty());
    }

    @Test
    void buildFamilyAgentContextResult_mapsUnifiedMetadata() {
        when(recallService.recall(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UnifiedAuthorizedMemoryRecallResult(
                        List.of(),
                        List.of(),
                        "UNIFIED_VECTOR_WITH_TEXT_FALLBACK",
                        List.of("bedtime", "bedtime reflection"),
                        12,
                        null));

        AgentMemoryContextResult result = facade.buildFamilyAgentContextResult(
                10L,
                101L,
                "bedtime",
                List.of());

        assertEquals("bedtime | bedtime reflection", result.metadata().retrievalQuery());
        assertEquals(12, result.metadata().rag().embeddingReadyCount());
        assertEquals("UNIFIED_VECTOR_WITH_TEXT_FALLBACK", result.metadata().rag().retrievalMode());
    }

    private static MemoryEntry entry() {
        MemoryEntry memory = new MemoryEntry();
        memory.setId(9L);
        memory.setUserId(202L);
        memory.setLibraryKind("FAMILY");
        memory.setType("EXPERIENCE");
        memory.setTitle("Bedtime routine");
        memory.setContent("Brush teeth before sleep and keep the reminder gentle.");
        return memory;
    }
}
