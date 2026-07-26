package com.familyagent.module.growth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class GrowthGuardRecord {

    @JsonIgnore
    private Long memoryEntryId;

    private Long id;

    private Long familyId;
    private Long targetUserId;
    private Long createdBy;
    private String category;
    private String content;
    private Integer severity;
    private LocalDate observedAt;
    private LocalDate followUpAt;
    private String visibility;
    private String status;

    private Object metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
