package com.familyagent.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentResponsePlanPayload(
        @JsonProperty("answer_depth") String answerDepth,
        @JsonProperty("recall_depth") String recallDepth,
        @JsonProperty("web_search_policy") String webSearchPolicy,
        @JsonProperty("decision_support") boolean decisionSupport,
        boolean degraded) {

    public AgentResponsePlanPayload {
        answerDepth = valueOrDefault(answerDepth, "STANDARD");
        recallDepth = valueOrDefault(recallDepth, "STANDARD");
        webSearchPolicy = valueOrDefault(webSearchPolicy, "AUTO");
    }

    public static AgentResponsePlanPayload defaults() {
        return new AgentResponsePlanPayload("STANDARD", "STANDARD", "AUTO", false, false);
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
