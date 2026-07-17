package com.familyagent.module.agent.constant;

public final class AgentSaveMemorySkillContract {

    public static final String SKILL_NAME = "save_memory";
    public static final String SKILL_VERSION = "1.0.0";
    public static final String PROMPT_VERSION = AgentAiArtifactVersions.SAVE_MEMORY_PROMPT;
    public static final String SCHEMA_VERSION = AgentAiArtifactVersions.SAVE_TOOL_PLAN_SCHEMA;
    public static final String OPERATION = "skill.save_memory.plan";

    private AgentSaveMemorySkillContract() {
    }
}
