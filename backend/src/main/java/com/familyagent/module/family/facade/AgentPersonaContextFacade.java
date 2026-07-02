package com.familyagent.module.family.facade;

import com.familyagent.module.family.dto.PersonaMaterialVO;
import com.familyagent.module.family.dto.PersonaMemberVO;
import com.familyagent.module.family.service.FamilyPersonaMaterialService;
import com.familyagent.module.family.service.FamilyPersonaMemberQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentPersonaContextFacade {

    private static final int MATERIAL_LIMIT = 6;
    private static final int MATERIAL_PREVIEW_LIMIT = 800;

    private final FamilyPersonaMemberQueryService personaQueryService;
    private final FamilyPersonaMaterialService materialService;

    public String buildPersonaAgentContext(Long familyId, Long personaId) {
        PersonaMemberVO persona = personaQueryService.getById(familyId, personaId);
        List<PersonaMaterialVO> materials = materialService.list(familyId, personaId);
        return String.join("\n\n",
                "persona_mode_rules:\n"
                        + "- Answer as the persona in direct conversation by default, not as a narrator analyzing the user.\n"
                        + "- Keep greetings and simple checks brief before adding persona color.\n"
                        + "- Avoid interpreting the user's motive unless the user asks for analysis.\n"
                        + "- Stay loyal to the profile and keep a stable voice.\n"
                        + "- Do not claim to be a real living family member.",
                personaProfile(persona),
                personaMaterials(materials));
    }

    private String personaProfile(PersonaMemberVO persona) {
        List<String> lines = new ArrayList<>();
        lines.add("persona_profile:");
        addLine(lines, "name", persona.getName());
        addLine(lines, "era_identity", persona.getEraIdentity());
        addLine(lines, "description", persona.getDescription());
        addLine(lines, "values", persona.getValues());
        addLine(lines, "speaking_style", persona.getSpeakingStyle());
        addLine(lines, "personality", persona.getPersonality());
        return String.join("\n", lines);
    }

    private String personaMaterials(List<PersonaMaterialVO> materials) {
        List<PersonaMaterialVO> safeMaterials = materials == null ? List.of() : materials;
        if (safeMaterials.isEmpty()) {
            return "persona_materials:\nNo material cards are available. Extend only from the base profile.";
        }
        return "persona_materials:\n" + safeMaterials.stream()
                .limit(MATERIAL_LIMIT)
                .map(this::materialLine)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String materialLine(PersonaMaterialVO material) {
        return "- " + textOrDefault(material.getTitle(), "Untitled")
                + "\n" + preview(material.getContent());
    }

    private void addLine(List<String> lines, String key, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(key + ": " + value.trim());
        }
    }

    private String preview(String value) {
        String text = textOrDefault(value, "");
        return text.length() <= MATERIAL_PREVIEW_LIMIT ? text : text.substring(0, MATERIAL_PREVIEW_LIMIT);
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
