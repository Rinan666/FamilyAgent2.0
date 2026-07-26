package com.familyagent.module.memory.service;

import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.memory.dto.PersonalMemoryView;
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

    public List<PersonalMemoryView> listMine(int limit) {
        List<MemoryEntry> entries = memoryRepository.findActivePersonalByUserId(
                CurrentUserGuard.currentUserId(),
                normalizeLimit(limit));
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> familyIdsByMemory = grantRepository.findByMemoryIds(
                        entries.stream().map(MemoryEntry::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        PersonalMemoryFamilyGrant::getMemoryId,
                        Collectors.mapping(PersonalMemoryFamilyGrant::getFamilyId, Collectors.toList())));
        return entries.stream()
                .map(entry -> PersonalMemoryView.from(entry, familyIdsByMemory.get(entry.getId())))
                .toList();
    }

    private int normalizeLimit(int value) {
        return value <= 0 ? 20 : Math.min(value, 100);
    }
}
