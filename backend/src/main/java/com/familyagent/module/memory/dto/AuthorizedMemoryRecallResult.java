package com.familyagent.module.memory.dto;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.memory.entity.MemoryEntry;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthorizedMemoryRecallResult {

    private List<DiaryEntry> diaries;
    private List<MemoryEntry> memories;
    private String retrievalMode;
    private String query;
    private long embeddingReadyCount;
}
