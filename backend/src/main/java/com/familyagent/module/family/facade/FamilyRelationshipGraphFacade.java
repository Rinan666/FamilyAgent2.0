package com.familyagent.module.family.facade;

import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.entity.FamilyRelationship;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.repository.FamilyRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FamilyRelationshipGraphFacade {

    private static final String SELF_LABEL = "本人";
    private static final String FAMILY_MEMBER_LABEL = "家族成员";

    private final FamilyMemberRepository memberRepository;
    private final FamilyRelationshipRepository relationshipRepository;

    public FamilyRelationshipGraphView resolve(
            Long familyId,
            Long viewerUserId,
            Long targetUserId,
            Collection<Long> requestedUserIds) {
        Set<Long> requested = requestedUserIds == null
                ? Set.of()
                : requestedUserIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (requested.isEmpty()) {
            return new FamilyRelationshipGraphView(Map.of());
        }

        Map<Long, FamilyMemberVO> members = memberRepository.findMemberViewsByFamilyId(familyId).stream()
                .filter(member -> requested.contains(member.getUserId()))
                .collect(Collectors.toMap(FamilyMemberVO::getUserId, Function.identity()));
        Map<RelationshipKey, FamilyRelationship> relationships = relationshipRepository.findByFamilyId(familyId)
                .stream()
                .collect(Collectors.toMap(
                        relationship -> new RelationshipKey(
                                relationship.getFromUserId(),
                                relationship.getToUserId()),
                        Function.identity(),
                        (left, right) -> left));

        Map<Long, FamilyRelationshipNode> resolved = new LinkedHashMap<>();
        for (Long userId : requested) {
            FamilyMemberVO member = members.get(userId);
            if (member == null) {
                continue;
            }
            String displayName = displayName(member);
            resolved.put(userId, new FamilyRelationshipNode(
                    userId,
                    displayName,
                    relationshipLabel(viewerUserId, userId, displayName, relationships),
                    targetUserId == null
                            ? null
                            : relationshipLabel(targetUserId, userId, displayName, relationships),
                    userId.equals(viewerUserId),
                    userId.equals(targetUserId)));
        }
        return new FamilyRelationshipGraphView(resolved);
    }

    private String relationshipLabel(
            Long fromUserId,
            Long toUserId,
            String fallback,
            Map<RelationshipKey, FamilyRelationship> relationships) {
        if (Objects.equals(fromUserId, toUserId)) {
            return SELF_LABEL;
        }
        FamilyRelationship direct = relationships.get(new RelationshipKey(fromUserId, toUserId));
        String directLabel = direct == null ? null : normalize(direct.getLabel());
        if (directLabel != null) {
            return directLabel;
        }
        FamilyRelationship reverse = relationships.get(new RelationshipKey(toUserId, fromUserId));
        String reverseLabel = reverse == null ? null : normalize(reverse.getReverseLabel());
        return reverseLabel == null ? fallback : reverseLabel;
    }

    private String displayName(FamilyMemberVO member) {
        String nickname = normalize(member.getNickname());
        if (nickname != null) {
            return nickname;
        }
        String username = normalize(member.getUsername());
        return username == null ? FAMILY_MEMBER_LABEL : username;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record RelationshipKey(Long fromUserId, Long toUserId) {
    }
}
