package com.familyagent.module.memory.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.service.MemoryEmbeddingService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemoryIndexingFacadeTest {

    @Test
    void shouldDelegateAllAfterCommitIndexOperations() {
        MemoryEmbeddingService embeddingService = mock(MemoryEmbeddingService.class);
        DiaryEntry diary = new DiaryEntry();
        MemoryEntry memory = new MemoryEntry();
        GrowthGuardRecord growthRecord = new GrowthGuardRecord();
        MemoryIndexingFacade facade = new MemoryIndexingFacade(embeddingService);

        facade.indexDiaryAfterCommit(diary);
        facade.indexMemoryAfterCommit(memory);
        facade.indexGrowthAfterCommit(growthRecord);

        verify(embeddingService).indexDiaryAfterCommit(diary);
        verify(embeddingService).indexMemoryAfterCommit(memory);
        verify(embeddingService).indexGrowthAfterCommit(growthRecord);
    }
}
