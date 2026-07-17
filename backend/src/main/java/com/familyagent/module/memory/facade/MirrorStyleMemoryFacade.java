package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MirrorStyleMemoryFacade {

    private final MemoryEntryRepository memoryRepository;

    public List<MemoryEntry> findActiveByFamilyAndUser(Long familyId, Long userId, int limit) {
        return memoryRepository.findActiveByFamilyAndUserForStyle(familyId, userId, limit);
    }
}
