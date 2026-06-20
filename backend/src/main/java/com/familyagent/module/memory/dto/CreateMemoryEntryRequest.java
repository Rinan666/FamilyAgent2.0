package com.familyagent.module.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class CreateMemoryEntryRequest {
    private Long familyId;
    @Size(max = 64)
    private String subject;

    @Size(max = 64)
    private String type;

    @Size(max = 64)
    private String scope;

    @NotBlank
    @Size(max = 8000)
    private String content;

    @Size(max = 1000)
    private String summary;
    private Integer importance;
    private BigDecimal confidence;
    private Long sourceSessionId;

    @Size(max = 50)
    private Map<String, Object> metadata;
}
