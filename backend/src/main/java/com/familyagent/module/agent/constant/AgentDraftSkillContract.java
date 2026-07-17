package com.familyagent.module.agent.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AgentDraftSkillContract {
    ORGANIZE_DRAFT(
            "organize_draft",
            "1.0.0",
            AgentAiArtifactVersions.ORGANIZE_DRAFT_PROMPT,
            AgentAiArtifactVersions.ORGANIZED_DRAFT_SCHEMA,
            "skill.organize_draft",
            "organize_draft"),
    PERSONA_MATERIAL_DRAFT(
            "persona_material_draft",
            "1.0.0",
            AgentAiArtifactVersions.PERSONA_MATERIAL_PROMPT,
            AgentAiArtifactVersions.PERSONA_MATERIAL_SCHEMA,
            "skill.persona_material_draft",
            "persona_material_draft");

    public static final String SOURCE = "FAMILY_DRAFT_EDITOR";
    public static final String AGENT_MODE = "family_draft";
    public static final String SUBJECT = "FamilyAgent";

    private final String skillName;
    private final String skillVersion;
    private final String promptVersion;
    private final String schemaVersion;
    private final String operation;
    private final String contextLabel;
}
