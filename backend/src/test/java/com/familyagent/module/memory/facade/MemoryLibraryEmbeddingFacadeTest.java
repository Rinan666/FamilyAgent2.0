package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.repository.MemoryEmbeddingWriteRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemoryLibraryEmbeddingFacadeTest {

    @Test
    void shouldMapMemoryLibrarySourcesToEmbeddingRepository() {
        MemoryEmbeddingWriteRepository repository = mock(MemoryEmbeddingWriteRepository.class);
        MemoryLibraryEmbeddingFacade facade = new MemoryLibraryEmbeddingFacade(repository);

        facade.deleteDiaryIndex(44L);
        facade.deleteMemoryIndex(88L);
        facade.deleteGrowthIndex(55L);

        verify(repository).deleteBySource("DIARY", 44L);
        verify(repository).deleteBySource("MEMORY", 88L);
        verify(repository).deleteBySource("GROWTH_OBSERVATION", 55L);
    }
}
