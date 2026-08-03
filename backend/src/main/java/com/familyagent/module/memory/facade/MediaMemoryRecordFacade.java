package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaMemoryRecordFacade {

    private final MemoryEntryRepository memoryRepository;

    public MemoryEntry findById(Long memoryId) {
        return memoryRepository.selectById(memoryId);
    }

    public MemoryEntry findVisibleById(Long familyId, Long memoryId, Long viewerUserId) {
        return memoryRepository.findVisibleFamilyMemoryById(familyId, memoryId, viewerUserId);
    }
}
