package com.familyagent.module.memory.facade;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentMemoryContextFacade {

    private static final String FAMILY_AGENT_SCENE = "FAMILY_AGENT";
    private static final int DIARY_LIMIT = 8;
    private static final int MEMORY_LIMIT = 8;

    private final AuthorizedMemoryRecallService recallService;
    private final AgentMemoryContextFormatter formatter;

    public String buildFamilyAgentContext(
            Long familyId,
            Long viewerUserId,
            String memberMessage,
            List<String> recentUserMessages) {
        return buildFamilyAgentContextResult(
                familyId,
                viewerUserId,
                memberMessage,
                recentUserMessages).context();
    }

    public AgentMemoryContextResult buildFamilyAgentContextResult(
            Long familyId,
            Long viewerUserId,
            String memberMessage,
            List<String> recentUserMessages) {
        try {
            AuthorizedMemoryRecallResult recall = recallService.recallForFamily(
                    familyId,
                    viewerUserId,
                    buildRecallQuery(memberMessage, recentUserMessages),
                    FAMILY_AGENT_SCENE,
                    DIARY_LIMIT,
                    MEMORY_LIMIT);
            return AgentMemoryContextResult.fromRecall(formatter.format(recall), recall);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Failed to build FamilyAgent memory context: familyId={}, viewerUserId={}",
                    familyId, viewerUserId, e);
            return AgentMemoryContextResult.empty();
        }
    }

    private String buildRecallQuery(String memberMessage, List<String> recentUserMessages) {
        Set<String> parts = new LinkedHashSet<>();
        List<String> history = recentUserMessages == null ? List.of() : recentUserMessages;
        int start = Math.max(0, history.size() - 2);
        for (String item : history.subList(start, history.size())) {
            addNonBlank(parts, item);
        }
        addNonBlank(parts, memberMessage);
        return String.join(" ", parts).trim();
    }

    private void addNonBlank(Set<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }
}
