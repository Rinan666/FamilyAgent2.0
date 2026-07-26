package com.familyagent.module.memory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateFamilyMemoryRequest {

    @NotNull
    private Long familyId;

    @NotBlank
    @Size(max = 8000)
    private String content;

    @Size(max = 64)
    private String type;

    @Size(max = 64)
    private String scope;

    @Size(max = 1000)
    private String summary;
    private Integer importance;
    private Long relatedUserId;
    private List<@Size(max = 40) String> tags;

    @Valid
    private WriteMemoryMetadata metadata;
}
