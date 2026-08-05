package com.familyagent.module.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class WriteMemoryRequest {

    @NotNull
    private Long familyId;

    @NotBlank
    private String memoryLibrary;

    @NotBlank
    private String memoryType;

    @NotBlank
    private String content;

    private String title;
    private List<String> tags;
    private String visibility;
    private Long relatedUserId;
    private List<Long> selectedFamilyIds;

    private WriteMemoryMetadata metadata;
}
