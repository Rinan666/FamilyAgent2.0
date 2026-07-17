package com.familyagent.module.agent.harness.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.module.agent.constant.AgentAiArtifactVersions;
import com.familyagent.module.agent.harness.eval.dto.CoreAiOutputManifestItem;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreAiOutputManifestTest {

    @Test
    void coreCapabilitiesDeclareApplicableVersionsAndEvalBindings() throws Exception {
        Map<String, CoreAiOutputManifestItem> items = CoreAiOutputManifest.items().stream()
                .collect(Collectors.toMap(CoreAiOutputManifestItem::capability, Function.identity()));

        assertEquals(5, items.size());
        assertVersion(items.get("family_chat").promptVersion());
        assertEquals(
                AgentAiArtifactVersions.FAMILY_CHAT_STREAM_SCHEMA,
                items.get("family_chat").schemaVersion());
        assertSkillVersions(items.get("save_memory_plan"));
        assertSkillVersions(items.get("organize_draft"));
        assertSkillVersions(items.get("persona_material_draft"));
        assertVersion(items.get("memory_recall_ranking").algorithmVersion());
        assertTrue(items.values().stream().allMatch(CoreAiOutputManifestItem::providerObservationRequiredWhenExternal));
        assertTrue(items.values().stream().allMatch(item -> hasText(item.evalBinding())));

        String json = new ObjectMapper().writeValueAsString(CoreAiOutputManifest.items());
        assertFalse(json.contains("prompt content"));
        assertFalse(json.contains("model output"));
        assertFalse(json.contains("family data"));
    }

    private static void assertSkillVersions(CoreAiOutputManifestItem item) {
        assertNotNull(item);
        assertVersion(item.skillVersion());
        assertVersion(item.promptVersion());
        assertVersion(item.schemaVersion());
    }

    private static void assertVersion(String value) {
        assertTrue(hasText(value));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
