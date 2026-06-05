package com.familyagent.module.memory.dto;

import lombok.Data;

@Data
public class MemoryRecallRequest {
    private String query;
    private String subject;
    private Long knowledgePointId;
    private Integer limit;
}
