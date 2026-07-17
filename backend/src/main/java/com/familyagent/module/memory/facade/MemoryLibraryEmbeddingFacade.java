package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.repository.MemoryEmbeddingWriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryLibraryEmbeddingFacade {

    private static final String DIARY_SOURCE = "DIARY";
    private static final String MEMORY_SOURCE = "MEMORY";
    private static final String GROWTH_SOURCE = "GROWTH_OBSERVATION";

    private final MemoryEmbeddingWriteRepository embeddingRepository;

    public void deleteDiaryIndex(Long diaryId) {
        embeddingRepository.deleteBySource(DIARY_SOURCE, diaryId);
    }

    public void deleteMemoryIndex(Long memoryId) {
        embeddingRepository.deleteBySource(MEMORY_SOURCE, memoryId);
    }

    public void deleteGrowthIndex(Long growthRecordId) {
        embeddingRepository.deleteBySource(GROWTH_SOURCE, growthRecordId);
    }
}
