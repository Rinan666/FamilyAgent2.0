package com.familyagent.module.family.facade;

import java.util.Map;

public record FamilyRelationshipGraphView(Map<Long, FamilyRelationshipNode> members) {

    private static final String FAMILY_MEMBER_LABEL = "家族成员";

    public FamilyRelationshipGraphView {
        members = members == null ? Map.of() : Map.copyOf(members);
    }

    public FamilyRelationshipNode member(Long userId) {
        FamilyRelationshipNode resolved = members.get(userId);
        if (resolved != null) {
            return resolved;
        }
        return new FamilyRelationshipNode(
                userId,
                FAMILY_MEMBER_LABEL,
                FAMILY_MEMBER_LABEL,
                null,
                false,
                false);
    }
}
