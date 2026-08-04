package com.familyagent.module.memory.facade;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.dto.MemoryRecallPlan;
import com.familyagent.module.memory.dto.UnifiedAuthorizedMemoryRecallResult;
import com.familyagent.module.memory.service.UnifiedAuthorizedMemoryRecallService;
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

    private final UnifiedAuthorizedMemoryRecallService recallService;
    private final AgentUnifiedMemoryContextFormatter formatter;

    public String buildFamilyAgentContext(
            Long familyId,
            Long viewerUserId,
            String memberMessage,
            List<String> recentUserMessages) {
        return buildFamilyAgentContextResult(
                familyId,
                viewerUserId,
                memberMessage,
                recentUserMessages,
                new MemoryRecallPlan(
                        com.familyagent.common.constant.MemoryRecallDepth.STANDARD,
                        List.of(buildRecallQuery(memberMessage, recentUserMessages)),
                        List.of(),
                        null)).context();
    }

    public AgentMemoryContextResult buildFamilyAgentContextResult(
            Long familyId,
            Long viewerUserId,
            String memberMessage,
            List<String> recentUserMessages) {
        return buildFamilyAgentContextResult(
                familyId,
                viewerUserId,
                memberMessage,
                recentUserMessages,
                new MemoryRecallPlan(
                        com.familyagent.common.constant.MemoryRecallDepth.STANDARD,
                        List.of(buildRecallQuery(memberMessage, recentUserMessages)),
                        List.of(),
                        null));
    }

    public AgentMemoryContextResult buildFamilyAgentContextResult(
            Long familyId,
            Long viewerUserId,
            String memberMessage,
            List<String> recentUserMessages,
            MemoryRecallPlan plan) {
        try {
            UnifiedAuthorizedMemoryRecallResult recall = recallService.recall(
                    familyId,
                    viewerUserId,
                    plan == null ? null : plan.preferredSubjectUserId(),
                    plan);
            return AgentMemoryContextResult.fromUnified(formatter.format(recall, plan), recall);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Failed to build FamilyAgent memory context: familyId={}, viewerUserId={}, errorType={}",
                    familyId, viewerUserId, e.getClass().getSimpleName());
            return AgentMemoryContextResult.failed(AgentMemoryContextErrorCode.RECALL_FAILED);
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
