package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.repository.MemoryEmbeddingWriteRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemoryLibraryEmbeddingFacadeTest {

    @Test
    void shouldDeleteUnifiedMemoryEmbedding() {
        MemoryEmbeddingWriteRepository repository = mock(MemoryEmbeddingWriteRepository.class);
        MemoryLibraryEmbeddingFacade facade = new MemoryLibraryEmbeddingFacade(repository);

        facade.deleteMemoryIndex(88L);

        verify(repository).deleteBySource("MEMORY", 88L);
    }
}
