package com.familyagent.module.agent.harness.constant;

public enum AgentRunErrorCode {
    REQUEST_REJECTED("AGENT_RUN_REQUEST_REJECTED"),
    RATE_LIMITED("AGENT_RUN_RATE_LIMITED"),
    STREAM_UNAVAILABLE("AI_STREAM_UNAVAILABLE"),
    STREAM_EOF("AI_STREAM_EOF"),
    IO_ERROR("AGENT_RUN_IO_ERROR"),
    EXECUTION_FAILED("AGENT_RUN_EXECUTION_FAILED");

    private final String code;

    AgentRunErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
