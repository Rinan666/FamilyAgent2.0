package com.familyagent.module.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdatePersonalMemoryVisibilityRequest {

    @NotBlank
    @Size(max = 64)
    private String visibility;

    @Size(max = 20)
    private List<Long> selectedFamilyIds;
}
