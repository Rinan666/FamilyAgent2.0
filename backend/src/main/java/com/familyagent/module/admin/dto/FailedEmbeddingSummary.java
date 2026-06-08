package com.familyagent.module.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FailedEmbeddingSummary {

    private Long id;
    private Long familyId;
    private String sourceType;
    private Long sourceId;
    private String error;
    private LocalDateTime updatedAt;
}
