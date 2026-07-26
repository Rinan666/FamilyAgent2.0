package com.familyagent.module.family.facade;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FamilyMembershipQueryFacade {

    private final FamilyMemberRepository memberRepository;

    public Set<Long> familyIdsForUser(Long userId) {
        return memberRepository.findByUserId(userId).stream()
                .map(FamilyMember::getFamilyId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isMember(Long familyId, Long userId) {
        return familyId != null
                && userId != null
                && memberRepository.findByFamilyAndUser(familyId, userId) != null;
    }

    public Set<Long> requireMemberships(Long userId, Collection<Long> requestedFamilyIds) {
        Set<Long> requested = requestedFamilyIds == null
                ? Set.of()
                : requestedFamilyIds.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toUnmodifiableSet());
        Set<Long> memberships = familyIdsForUser(userId);
        if (!memberships.containsAll(requested)) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER, "Selected family is not available");
        }
        return requested;
    }
}
