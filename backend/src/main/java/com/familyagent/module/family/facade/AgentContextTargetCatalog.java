package com.familyagent.module.family.facade;

import java.util.List;

public record AgentContextTargetCatalog(
        List<AgentContextTarget> members,
        List<AgentContextTarget> personas) {

    public AgentContextTargetCatalog {
        members = members == null ? List.of() : List.copyOf(members);
        personas = personas == null ? List.of() : List.copyOf(personas);
    }
}
