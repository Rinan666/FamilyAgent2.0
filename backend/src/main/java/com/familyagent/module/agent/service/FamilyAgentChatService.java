package com.familyagent.module.agent.service;

import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.agent.dto.AgentChatRequest;
import com.familyagent.module.agent.dto.AgentChatResponse;
import com.familyagent.module.family.entity.Family;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the mobile family chat flow.
 */
@Service
@RequiredArgsConstructor
public class FamilyAgentChatService {

    private static final int FAMILY_MEMORY_LIMIT = 6;

    private final AIServiceClient aiServiceClient;
    private final FamilyService familyService;
    private final AuthorizedMemoryRecallService authorizedMemoryRecallService;

    public AgentChatResponse chat(AgentChatRequest request, String authorization) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        Family family = familyService.getFamily(request.getFamilyId());
        FamilyMember membership = familyService.getFamilyMember(request.getFamilyId(), viewerUserId);

        AuthorizedMemoryRecallResult recall = authorizedMemoryRecallService.recallForFamilyAfterViewerValidated(
                request.getFamilyId(),
                viewerUserId,
                request.getMessage(),
                "FAMILY_AGENT",
                0,
                FAMILY_MEMORY_LIMIT
        );

        AIServiceClient.ChatCompletionResponse completion = aiServiceClient.completeChat(
                request.toAiPayload(family.getName(), membership.getRole(), buildMemoryContext(family, recall.getMemories())),
                authorization
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (completion.metadata() != null) {
            metadata.putAll(completion.metadata());
        }
        metadata.put("familyId", request.getFamilyId());
        metadata.put("memoryCount", recall.getMemoryCount());
        metadata.put("retrievalMode", recall.getRetrievalMode());
        metadata.put("embeddingReadyCount", recall.getEmbeddingReadyCount());

        return AgentChatResponse.builder()
                .content(completion.content())
                .metadata(metadata)
                .build();
    }

    private static String buildMemoryContext(Family family, List<MemoryEntry> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Family: ").append(family.getName()).append('\n');
        builder.append("Relevant family memories:\n");
        int index = 1;
        for (MemoryEntry memory : memories) {
            if (memory == null || memory.getContent() == null || memory.getContent().isBlank()) {
                continue;
            }
            builder.append(index++)
                    .append(". [")
                    .append(memory.getType())
                    .append("] ");
            if (memory.getSummary() != null && !memory.getSummary().isBlank()) {
                builder.append(memory.getSummary().trim()).append(" - ");
            }
            builder.append(memory.getContent().trim()).append('\n');
        }
        return builder.toString().trim();
    }
}
