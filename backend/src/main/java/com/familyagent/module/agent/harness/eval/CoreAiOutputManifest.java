package com.familyagent.module.agent.harness.eval;

import com.familyagent.module.agent.constant.AgentAiArtifactVersions;
import com.familyagent.module.agent.constant.AgentDraftSkillContract;
import com.familyagent.module.agent.constant.AgentSaveMemorySkillContract;
import com.familyagent.module.agent.harness.eval.dto.CoreAiOutputManifestItem;
import com.familyagent.module.memory.facade.MemoryRecallContract;

import java.util.List;

public final class CoreAiOutputManifest {

    private static final List<CoreAiOutputManifestItem> ITEMS = List.of(
            new CoreAiOutputManifestItem(
                    "family_chat",
                    "ai-service",
                    null,
                    AgentAiArtifactVersions.FAMILY_CHAT_PROMPT,
                    AgentAiArtifactVersions.FAMILY_CHAT_STREAM_SCHEMA,
                    null,
                    true,
                    "family-chat-stream-contract"),
            new CoreAiOutputManifestItem(
                    "save_memory_plan",
                    "ai-service",
                    AgentSaveMemorySkillContract.SKILL_VERSION,
                    AgentSaveMemorySkillContract.PROMPT_VERSION,
                    AgentSaveMemorySkillContract.SCHEMA_VERSION,
                    null,
                    true,
                    "save-memory-plan-eval"),
            draftItem("organize_draft", AgentDraftSkillContract.ORGANIZE_DRAFT, "organize-draft-eval"),
            draftItem(
                    "persona_material_draft",
                    AgentDraftSkillContract.PERSONA_MATERIAL_DRAFT,
                    "persona-material-draft-eval"),
            new CoreAiOutputManifestItem(
                    "memory_recall_ranking",
                    "backend",
                    null,
                    null,
                    null,
                    MemoryRecallContract.ALGORITHM_VERSION,
                    true,
                    "memory-recall-quality-eval"));

    private CoreAiOutputManifest() {
    }

    public static List<CoreAiOutputManifestItem> items() {
        return ITEMS;
    }

    private static CoreAiOutputManifestItem draftItem(
            String capability,
            AgentDraftSkillContract contract,
            String evalBinding) {
        return new CoreAiOutputManifestItem(
                capability,
                "ai-service",
                contract.getSkillVersion(),
                contract.getPromptVersion(),
                contract.getSchemaVersion(),
                null,
                true,
                evalBinding);
    }
}
