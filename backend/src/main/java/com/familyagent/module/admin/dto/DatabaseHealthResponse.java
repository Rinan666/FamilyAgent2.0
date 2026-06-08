package com.familyagent.module.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DatabaseHealthResponse {

    private LocalDateTime generatedAt;
    private String databaseName;
    private boolean pgvectorInstalled;
    private long totalUsers;
    private long totalFamilies;
    private long totalCoreRecords;
    private long totalEmbeddings;
    private long readyEmbeddings;
    private long failedEmbeddings;
    private List<DatabaseTableCount> tableCounts;
    private List<EmbeddingStatusSummary> embeddingStatuses;
    private List<FamilyDatabaseSummary> families;
    private List<FailedEmbeddingSummary> recentFailedEmbeddings;
}
