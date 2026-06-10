package com.familyagent.module.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionArchiveRangeSummary {

    private Long sessionId;
    private Long archiveId;
    private int startSeq;
    private int endSeq;
    private int messageCount;
    private LocalDateTime createdAt;
}
