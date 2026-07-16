package com.familyagent.module.agent.dto;

public record AgentDraftResult<T>(Long skillRunId, T data) {
}
