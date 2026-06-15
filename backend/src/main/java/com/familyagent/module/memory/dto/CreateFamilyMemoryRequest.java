package com.familyagent.module.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class CreateFamilyMemoryRequest {

    @NotNull
    private Long familyId;

    @NotBlank
    private String content;

    private String type;
    private String scope;
    private String summary;
    private Integer importance;
    private Map<String, Object> metadata;
}
