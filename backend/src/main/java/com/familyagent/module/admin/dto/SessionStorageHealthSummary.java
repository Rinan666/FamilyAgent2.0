package com.familyagent.module.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionStorageHealthSummary {

    private Long sessionId;
    private Long familyId;
    private int messageCount;
    private int archivedBeforeSeq;
    private String archiveStatus;
    private long liveMessageRows;
    private long archivedMessageRows;
    private long totalMaterializedRows;
}
