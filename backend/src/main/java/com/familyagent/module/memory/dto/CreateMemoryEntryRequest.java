package com.familyagent.module.memory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class CreateMemoryEntryRequest {
    private Long familyId;
    private String subject;
    private Long knowledgePointId;
    private String type;
    private String scope;

    @NotBlank
    private String content;

    private String summary;
    private Integer importance;
    private BigDecimal confidence;
    private Long sourceSessionId;
    private Map<String, Object> metadata;
}
