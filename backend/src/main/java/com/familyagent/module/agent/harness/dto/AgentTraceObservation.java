package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.agent.harness.constant.AgentRunStepType;
import com.familyagent.module.agent.harness.constant.AgentTraceOperation;
import com.familyagent.module.agent.harness.constant.AgentTracePrivacyCategory;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record AgentTraceObservation(
        AgentRunStepType stepType,
        String operation,
        String provider,
        String model,
        String promptVersion,
        String skillVersion,
        Long latencyMs,
        boolean success,
        String errorCode,
        boolean degraded,
        List<AgentTracePrivacyCategory> privacyCategories
) {
    public AgentTraceObservation {
        privacyCategories = privacyCategories == null ? List.of() : List.copyOf(privacyCategories);
    }

    public static Optional<AgentTraceObservation> from(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        String operation = text(node, "operation");
        AgentRunStepType stepType = enumValue(AgentRunStepType.class, text(node, "stepType"));
        if (operation == null || !isSupportedOperation(stepType, operation)) {
            return Optional.empty();
        }
        return Optional.of(new AgentTraceObservation(
                stepType,
                operation,
                text(node, "provider"),
                text(node, "model"),
                text(node, "promptVersion"),
                text(node, "skillVersion"),
                nonNegativeLong(node.get("latencyMs")),
                node.path("success").asBoolean(false),
                text(node, "errorCode"),
                node.path("degraded").asBoolean(false),
                privacyCategories(node.path("privacyCategories"))));
    }

    private static boolean isSupportedOperation(AgentRunStepType stepType, String operation) {
        return (stepType == AgentRunStepType.LLM
                && AgentTraceOperation.LLM_CHAT_STREAM.value().equals(operation))
                || (stepType == AgentRunStepType.WEB_SEARCH
                && AgentTraceOperation.WEB_SEARCH_PUBLIC.value().equals(operation));
    }

    private static List<AgentTracePrivacyCategory> privacyCategories(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<AgentTracePrivacyCategory> categories = new ArrayList<>();
        node.forEach(item -> {
            AgentTracePrivacyCategory category = enumValue(
                    AgentTracePrivacyCategory.class,
                    item.isTextual() ? item.asText() : null);
            if (category != null && !categories.contains(category)) {
                categories.add(category);
            }
        });
        return List.copyOf(categories);
    }

    private static Long nonNegativeLong(JsonNode node) {
        return node != null && node.canConvertToLong() ? Math.max(0, node.asLong()) : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
