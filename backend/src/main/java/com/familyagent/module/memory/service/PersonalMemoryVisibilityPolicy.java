package com.familyagent.module.memory.service;

import com.familyagent.common.constant.PersonalMemoryVisibility;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.facade.FamilyMembershipQueryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PersonalMemoryVisibilityPolicy {

    private final FamilyMembershipQueryFacade membershipQueryFacade;

    public VisibilityGrant resolve(
            Long ownerUserId,
            String requestedVisibility,
            Collection<Long> selectedFamilyIds) {
        PersonalMemoryVisibility visibility = normalize(requestedVisibility);
        Set<Long> familyIds = switch (visibility) {
            case ALL_FAMILIES_VISIBLE -> membershipQueryFacade.familyIdsForUser(ownerUserId);
            case SELECTED_FAMILIES_VISIBLE -> requireSelectedFamilies(ownerUserId, selectedFamilyIds);
            case PRIVATE, CARE_VISIBLE -> Set.of();
        };
        return new VisibilityGrant(visibility.name(), familyIds.stream().sorted().toList());
    }

    private Set<Long> requireSelectedFamilies(Long ownerUserId, Collection<Long> selectedFamilyIds) {
        Set<Long> selected = selectedFamilyIds == null
                ? Set.of()
                : selectedFamilyIds.stream()
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (selected.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "At least one family must be selected");
        }
        return membershipQueryFacade.requireMemberships(ownerUserId, selected);
    }

    private PersonalMemoryVisibility normalize(String value) {
        String normalized = value == null || value.isBlank()
                ? PersonalMemoryVisibility.PRIVATE.name()
                : value.trim().toUpperCase(Locale.ROOT);
        if (!PersonalMemoryVisibility.names().contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Personal memory visibility is not supported");
        }
        return PersonalMemoryVisibility.valueOf(normalized);
    }

    public record VisibilityGrant(String visibility, List<Long> familyIds) {

        public VisibilityGrant {
            familyIds = familyIds == null ? List.of() : List.copyOf(familyIds);
        }
    }
}
