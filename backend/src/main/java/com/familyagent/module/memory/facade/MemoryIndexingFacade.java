package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.service.MemoryEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryIndexingFacade {

    private final MemoryEmbeddingService embeddingService;

    public void indexMemoryAfterCommit(MemoryEntry entry) {
        embeddingService.indexMemoryAfterCommit(entry);
    }
}
