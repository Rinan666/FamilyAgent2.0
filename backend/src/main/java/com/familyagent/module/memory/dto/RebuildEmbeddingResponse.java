package com.familyagent.module.memory.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RebuildEmbeddingResponse {

    private Long familyId;
    private int diaryCount;
    private int memoryCount;
    private int growthRecordCount;
    private int scheduledCount;
    private int indexedCount;
}
