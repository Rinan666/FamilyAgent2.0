package com.familyagent.module.memory.service;

import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.family.facade.FamilyRelationshipGraphFacade;
import com.familyagent.module.family.facade.FamilyRelationshipGraphView;
import com.familyagent.module.family.facade.FamilyRelationshipNode;
import com.familyagent.module.memory.dto.PersonalMemoryView;
import com.familyagent.module.memory.dto.SharedPersonalMemoryView;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.entity.PersonalMemoryFamilyGrant;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.repository.PersonalMemoryFamilyGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonalMemoryQueryService {

    private final MemoryEntryRepository memoryRepository;
    private final PersonalMemoryFamilyGrantRepository grantRepository;
    private final FamilyMembershipFacade familyMembershipFacade;
    private final FamilyRelationshipGraphFacade relationshipGraphFacade;

    public List<PersonalMemoryView> listMine(int limit) {
        List<MemoryEntry> entries = memoryRepository.findActivePersonalByUserId(
                CurrentUserGuard.currentUserId(),
                normalizeLimit(limit));
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> familyIdsByMemory = familyIdsByMemory(entries);
        return entries.stream()
                .map(entry -> PersonalMemoryView.from(entry, familyIdsByMemory.get(entry.getId())))
                .toList();
    }

    public List<SharedPersonalMemoryView> listSharedWithFamily(Long familyId, int limit) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        familyMembershipFacade.checkMembership(familyId, viewerUserId);
        List<MemoryEntry> entries = memoryRepository.findVisibleSharedPersonalMemories(
                familyId,
                viewerUserId,
                normalizeLimit(limit));
        if (entries.isEmpty()) {
            return List.of();
        }
        FamilyRelationshipGraphView relationships = relationshipGraphFacade.resolve(
                familyId,
                viewerUserId,
                null,
                entries.stream().map(MemoryEntry::getUserId).collect(Collectors.toSet()));
        return entries.stream()
                .map(entry -> toSharedView(entry, relationships))
                .toList();
    }

    private SharedPersonalMemoryView toSharedView(
            MemoryEntry entry,
            FamilyRelationshipGraphView relationships) {
        FamilyRelationshipNode owner = relationships.member(entry.getUserId());
        return SharedPersonalMemoryView.from(
                entry,
                owner.displayName(),
                owner.relationshipToViewer());
    }

    private Map<Long, List<Long>> familyIdsByMemory(List<MemoryEntry> entries) {
        return grantRepository.findByMemoryIds(entries.stream().map(MemoryEntry::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        PersonalMemoryFamilyGrant::getMemoryId,
                        Collectors.mapping(PersonalMemoryFamilyGrant::getFamilyId, Collectors.toList())));
    }

    private int normalizeLimit(int value) {
        return value <= 0 ? 20 : Math.min(value, 100);
    }
}
