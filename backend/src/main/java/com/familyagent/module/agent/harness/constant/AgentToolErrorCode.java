package com.familyagent.module.agent.harness.constant;

public enum AgentToolErrorCode {
    TOOL_NOT_FOUND("AGENT_TOOL_NOT_FOUND", 4401),
    INVALID_INPUT("AGENT_TOOL_INVALID_INPUT", 4402),
    PERMISSION_DENIED("AGENT_TOOL_PERMISSION_DENIED", 4403),
    CONFIRMATION_REQUIRED("AGENT_TOOL_CONFIRMATION_REQUIRED", 4404),
    EXECUTION_FAILED("AGENT_TOOL_EXECUTION_FAILED", 4405);

    private final String code;
    private final int businessCode;

    AgentToolErrorCode(String code, int businessCode) {
        this.code = code;
        this.businessCode = businessCode;
    }

    public String code() {
        return code;
    }

    public int businessCode() {
        return businessCode;
    }
}
