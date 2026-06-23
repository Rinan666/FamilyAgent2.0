package com.familyagent.module.agent.service;

import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.agent.dto.AgentChatRequest;
import com.familyagent.module.agent.dto.AgentChatResponse;
import com.familyagent.module.family.entity.Family;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyAgentChatServiceTest {

    @Mock private AIServiceClient aiServiceClient;
    @Mock private FamilyService familyService;
    @Mock private AuthorizedMemoryRecallService authorizedMemoryRecallService;
    @InjectMocks private FamilyAgentChatService familyAgentChatService;

    @Test
    void chat_shouldValidateFamilyAndIncludeMemoryContext() {
        AgentChatRequest request = new AgentChatRequest();
        request.setFamilyId(5L);
        request.setMessage("What should our family remember?");

        Family family = new Family();
        family.setId(5L);
        family.setName("Chen Family");

        FamilyMember member = new FamilyMember();
        member.setFamilyId(5L);
        member.setUserId(9L);
        member.setRole("MEMBER");

        MemoryEntry memory = new MemoryEntry();
        memory.setType("FAMILY_STORY");
        memory.setSummary("Weekend routine");
        memory.setContent("We review the week together every Sunday.");

        AuthorizedMemoryRecallResult recallResult = AuthorizedMemoryRecallResult.builder()
                .memories(List.of(memory))
                .memoryCount(1)
                .retrievalMode("TEXT_FALLBACK")
                .embeddingReadyCount(3)
                .build();

        when(familyService.getFamily(5L)).thenReturn(family);
        when(familyService.getFamilyMember(5L, 9L)).thenReturn(member);
        when(authorizedMemoryRecallService.recallForFamilyAfterViewerValidated(5L, 9L,
                "What should our family remember?", "FAMILY_AGENT", 0, 6))
                .thenReturn(recallResult);
        when(aiServiceClient.completeChat(org.mockito.ArgumentMatchers.anyMap(), eq("token-9")))
                .thenReturn(new AIServiceClient.ChatCompletionResponse("Answer", Map.of("source", "ai")));

        try (MockedStatic<com.familyagent.common.security.CurrentUserGuard> guard = mockStatic(com.familyagent.common.security.CurrentUserGuard.class)) {
            guard.when(com.familyagent.common.security.CurrentUserGuard::currentUserId).thenReturn(9L);

            AgentChatResponse response = familyAgentChatService.chat(request, "token-9");

            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            org.mockito.Mockito.verify(aiServiceClient).completeChat(payloadCaptor.capture(), eq("token-9"));
            String memoryContext = String.valueOf(payloadCaptor.getValue().get("memory_context"));
            assertTrue(memoryContext.contains("Weekend routine"));
            assertEquals("Answer", response.content());
            assertEquals(1, response.metadata().get("memoryCount"));
            assertEquals("TEXT_FALLBACK", response.metadata().get("retrievalMode"));
        }
    }
}
