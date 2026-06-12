package com.familyagent.module.memory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WriteMemoryRequest {

    @NotNull
    private Long familyId;

    @NotBlank
    private String writeCategory;

    @NotBlank
    private String content;

    private String title;
    private List<String> tags;
    private String visibility;
    private Long relatedUserId;
    private String diaryEntryType;
    private String memoryType;
    private String growthCategory;

    @Min(1)
    @Max(5)
    private Integer growthSeverity;

    private Map<String, Object> metadata;
}
