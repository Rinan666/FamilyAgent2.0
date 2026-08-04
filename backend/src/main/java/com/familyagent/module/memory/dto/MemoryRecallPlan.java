package com.familyagent.module.memory.dto;

import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryRecallDepth;

import java.util.List;

public record MemoryRecallPlan(
        MemoryRecallDepth depth,
        List<String> queries,
        List<MemoryContentType> preferredTypes,
        Long preferredSubjectUserId) {

    public MemoryRecallPlan {
        depth = depth == null ? MemoryRecallDepth.STANDARD : depth;
        queries = queries == null ? List.of() : queries.stream()
                .filter(query -> query != null && !query.isBlank())
                .map(String::trim)
                .distinct()
                .limit(3)
                .toList();
        preferredTypes = preferredTypes == null ? List.of() : List.copyOf(preferredTypes);
    }

    public int resultLimit() {
        return depth.resultLimit();
    }

    public int candidateLimit() {
        return depth.candidateLimit();
    }

    public int contextCharBudget() {
        return depth.contextCharBudget();
    }

    public boolean enabled() {
        return depth != MemoryRecallDepth.NONE && resultLimit() > 0 && !queries.isEmpty();
    }
}
