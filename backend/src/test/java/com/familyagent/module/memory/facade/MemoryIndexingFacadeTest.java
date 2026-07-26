package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.service.MemoryEmbeddingService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemoryIndexingFacadeTest {

    @Test
    void shouldDelegateUnifiedMemoryIndexing() {
        MemoryEmbeddingService embeddingService = mock(MemoryEmbeddingService.class);
        MemoryEntry memory = new MemoryEntry();
        MemoryIndexingFacade facade = new MemoryIndexingFacade(embeddingService);

        facade.indexMemoryAfterCommit(memory);

        verify(embeddingService).indexMemoryAfterCommit(memory);
    }
}
