package com.familyagent.module.admin.dto;

import com.familyagent.module.memory.dto.RecallSourceSummary;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MemoryRecallDiagnosticResponse {

    private Long familyId;
    private Long viewerUserId;
    private String query;
    private String retrievalMode;
    private long embeddingReadyCount;
    private int diaryCount;
    private int memoryCount;
    private int growthRecordCount;
    private List<RecallSourceSummary> sources;
}
