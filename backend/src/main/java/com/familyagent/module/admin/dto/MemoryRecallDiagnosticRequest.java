package com.familyagent.module.admin.dto;

import lombok.Data;

@Data
public class MemoryRecallDiagnosticRequest {

    private Long familyId;
    private Long viewerUserId;
    private String query;
    private Integer diaryLimit;
    private Integer memoryLimit;
}
