package com.familyagent.module.memory.dto;

import lombok.Data;

@Data
public class MemoryRecallRequest {
    private Long familyId;
    private Long targetUserId;
    private String query;
    private String scene;
    private String subject;
    private Integer limit;
    private Integer diaryLimit;
    private Integer memoryLimit;
}
