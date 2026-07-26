package com.familyagent.module.memory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePersonalMemoryRequest {

    @NotBlank
    @Size(max = 8000)
    private String content;

    @Size(max = 64)
    private String type;

    @Size(max = 64)
    private String visibility;

    @Size(max = 1000)
    private String summary;

    @Min(1)
    @Max(5)
    private Integer importance;

    @Size(max = 20)
    private List<Long> selectedFamilyIds;

    @Valid
    private WriteMemoryMetadata metadata;
}
