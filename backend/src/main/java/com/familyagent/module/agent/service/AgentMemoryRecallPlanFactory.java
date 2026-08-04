package com.familyagent.module.agent.service;

import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryRecallDepth;
import com.familyagent.module.agent.dto.AgentChatStreamRequest;
import com.familyagent.module.agent.dto.AgentIntentPlan;
import com.familyagent.module.memory.dto.MemoryRecallPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AgentMemoryRecallPlanFactory {

    public MemoryRecallPlan create(AgentChatStreamRequest request, AgentIntentPlan intentPlan) {
        MemoryRecallDepth depth = intentPlan.responsePlan().recallDepth();
        if (depth == MemoryRecallDepth.NONE) {
            return new MemoryRecallPlan(depth, List.of(), List.of(), intentPlan.targetUserId());
        }
        String query = buildQuery(intentPlan.effectiveMessage(), request.userHistoryContents());
        List<String> queries = new ArrayList<>();
        queries.add(query);
        if (depth == MemoryRecallDepth.DEEP) {
            queries.add(query + " 处境 选择 判断依据 行动代价");
            queries.add(query + " 结果 后续变化 复盘 可迁移经验");
        }
        return new MemoryRecallPlan(
                depth,
                queries,
                preferredTypes(intentPlan.effectiveMessage()),
                intentPlan.targetUserId());
    }

    private static String buildQuery(String message, List<String> recentUserMessages) {
        List<String> parts = new ArrayList<>();
        List<String> history = recentUserMessages == null ? List.of() : recentUserMessages;
        int start = Math.max(0, history.size() - 2);
        for (String item : history.subList(start, history.size())) {
            if (item != null && !item.isBlank()) {
                parts.add(item.trim());
            }
        }
        if (message != null && !message.isBlank()) {
            parts.add(message.trim());
        }
        return String.join(" ", parts).trim();
    }

    private static List<MemoryContentType> preferredTypes(String message) {
        String text = message == null ? "" : message;
        if (containsAny(text, "计划", "打算", "下一步")) {
            return List.of(MemoryContentType.PLAN, MemoryContentType.INSIGHT);
        }
        if (containsAny(text, "经历", "故事", "做过", "发生过")) {
            return List.of(MemoryContentType.EXPERIENCE, MemoryContentType.INSIGHT);
        }
        if (containsAny(text, "怎么看", "价值观", "为什么", "复盘", "选择")) {
            return List.of(MemoryContentType.INSIGHT, MemoryContentType.EXPERIENCE);
        }
        if (containsAny(text, "最近", "变化", "状态", "观察")) {
            return List.of(MemoryContentType.OBSERVATION, MemoryContentType.NOTE);
        }
        return List.of();
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
