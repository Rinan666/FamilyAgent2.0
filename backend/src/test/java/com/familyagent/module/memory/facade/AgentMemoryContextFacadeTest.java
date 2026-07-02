package com.familyagent.module.memory.facade;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
        assertTrue(context.contains("[ELDER_ADVICE] Brush teeth before sleep"));
    }
}
