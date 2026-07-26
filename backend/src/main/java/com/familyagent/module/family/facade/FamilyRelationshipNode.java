package com.familyagent.module.family.facade;

public record FamilyRelationshipNode(
        Long userId,
        String displayName,
        String relationshipToViewer,
        String relationshipToTarget,
        boolean currentViewer,
        boolean currentTarget) {
}
