package com.familyagent.module.agent.harness.constant;

public enum AgentToolName {
    RECALL_FAMILY_MEMORY("recall_family_memory"),
    CREATE_DIARY_ENTRY("create_diary_entry"),
    CREATE_FAMILY_MEMORY("create_family_memory"),
    CREATE_GROWTH_GUARD_RECORD("create_growth_guard_record");

    private final String value;

    AgentToolName(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
