package com.familyagent.module.memory.facade;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.dto.RecallSourceSummary;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMemoryContextFacadeTest {

    private final AuthorizedMemoryRecallService recallService = mock(AuthorizedMemoryRecallService.class);
    private final AgentMemoryContextFacade facade = new AgentMemoryContextFacade(
            recallService,
            new AgentMemoryContextFormatter());

    @Test
    void buildFamilyAgentContext_usesAuthorizedRecallAndFormatsPreview() {
        MemoryEntry memory = new MemoryEntry();
        memory.setUserId(202L);
        memory.setStatus(EntityStatus.ACTIVE.name());
        memory.setType("ELDER_ADVICE");
        memory.setContent("Brush teeth before sleep and keep the reminder gentle.");
        when(recallService.recallForFamily(
                10L,
                101L,
                "older turn earlier question how should we talk about teeth",
                "FAMILY_AGENT",
                8,
                8))
                .thenReturn(AuthorizedMemoryRecallResult.builder()
                        .memories(List.of(memory))
                        .diaries(List.of())
                        .growthRecords(List.of())
                        .retrievalMode("TEXT_FALLBACK")
                        .embeddingReadyCount(3)
                        .build());

        String context = facade.buildFamilyAgentContext(
                10L,
                101L,
                "how should we talk about teeth",
                List.of("older turn", "earlier question"));

        verify(recallService).recallForFamily(
                10L,
                101L,
                "older turn earlier question how should we talk about teeth",
                "FAMILY_AGENT",
                8,
                8);
        assertTrue(context.contains("retrieval_summary: mode=TEXT_FALLBACK embedding_ready=3"));
        assertTrue(context.contains("[ELDER_ADVICE] author=family_user_202 Brush teeth before sleep"));
    }

    @Test
    void buildFamilyAgentContextResult_exposesDegradedRecallFailureWithoutSensitiveDetails() {
        when(recallService.recallForFamily(10L, 101L, "hello", "FAMILY_AGENT", 8, 8))
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
    void buildFamilyAgentContextResult_mapsRecallMetadataToFacadeContract() {
        RecallSourceSummary source = RecallSourceSummary.builder()
                .id("memory-9")
                .sourceType("FAMILY_EXPERIENCE")
                .title("Bedtime routine")
                .topics(List.of("HEALTH"))
                .scenes(List.of("health"))
                .build();
        when(recallService.recallForFamily(10L, 101L, "bedtime", "FAMILY_AGENT", 8, 8))
                .thenReturn(AuthorizedMemoryRecallResult.builder()
                        .memories(List.of())
                        .diaries(List.of())
                        .growthRecords(List.of())
                        .retrievalMode("VECTOR_WITH_TEXT_FALLBACK")
                        .embeddingReadyCount(12)
                        .query("bedtime health")
                        .sources(List.of(source))
                        .build());

        AgentMemoryContextResult result = facade.buildFamilyAgentContextResult(
                10L,
                101L,
                "bedtime",
                List.of());

        assertEquals("bedtime health", result.metadata().retrievalQuery());
        assertEquals(12, result.metadata().rag().embeddingReadyCount());
        assertEquals("memory-9", result.metadata().rag().sources().get(0).id());
        assertEquals(List.of("HEALTH"), result.metadata().rag().sources().get(0).topics());
    }
}
