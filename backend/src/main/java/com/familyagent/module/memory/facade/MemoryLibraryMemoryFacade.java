package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryLibraryMemoryFacade {

    private final MemoryEntryRepository memoryRepository;

    public MemoryEntry findById(Long memoryId) {
        return memoryRepository.selectById(memoryId);
    }

    public void update(MemoryEntry entry) {
        memoryRepository.updateById(entry);
    }

    public void delete(Long memoryId) {
        memoryRepository.deleteById(memoryId);
    }
}
