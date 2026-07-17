package com.familyagent.module.agent.harness.eval.dto;

public record CoreAiOutputManifestItem(
        String capability,
        String owner,
        String skillVersion,
        String promptVersion,
        String schemaVersion,
        String algorithmVersion,
        boolean providerObservationRequiredWhenExternal,
        String evalBinding) {
}
