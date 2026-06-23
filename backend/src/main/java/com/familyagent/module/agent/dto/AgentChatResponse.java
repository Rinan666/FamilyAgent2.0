package com.familyagent.module.agent.dto;

import lombok.Builder;

import java.util.Map;

/**
 * Non-stream family chat response.
 */
@Builder
public record AgentChatResponse(
        String content,
        Map<String, Object> metadata
) {
}
