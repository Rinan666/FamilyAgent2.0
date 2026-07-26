package com.familyagent.module.memory.facade;

import com.familyagent.common.constant.MemoryEmbeddingSourceType;
import com.familyagent.module.memory.repository.MemoryEmbeddingWriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryLibraryEmbeddingFacade {

    private final MemoryEmbeddingWriteRepository embeddingRepository;

    public void deleteMemoryIndex(Long memoryId) {
        embeddingRepository.deleteBySource(MemoryEmbeddingSourceType.MEMORY.name(), memoryId);
    }
}
