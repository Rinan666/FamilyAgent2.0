package com.familyagent.module.family.facade;

import java.util.List;

public record AgentContextTarget(
        Long id,
        String displayName,
        List<String> aliases) {

    public AgentContextTarget {
        displayName = displayName == null || displayName.isBlank() ? "Unknown" : displayName.trim();
        aliases = aliases == null ? List.of() : aliases.stream()
                .filter(alias -> alias != null && !alias.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    public boolean matches(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.trim().toLowerCase();
        return aliases.stream().anyMatch(alias -> normalized.contains(alias.toLowerCase()));
    }
}
