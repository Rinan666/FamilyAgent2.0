package com.familyagent.module.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EmbeddingStatusSummary {

    private Long familyId;
    private String sourceType;
    private String status;
    private long count;
    private LocalDateTime lastUpdatedAt;
}
