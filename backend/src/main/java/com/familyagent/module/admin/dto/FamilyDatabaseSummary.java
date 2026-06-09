package com.familyagent.module.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FamilyDatabaseSummary {

    private Long familyId;
    private String familyName;
    private long memberCount;
    private long diaryCount;
    private long memoryCount;
    private long growthRecordCount;
    private long skillRunCount;
    private long failedSkillRunCount;
    private long readyEmbeddingCount;
    private long failedEmbeddingCount;
    private Long ownerUserId;
    private String ownerDisplayName;
    private boolean ownerMissing;
}
