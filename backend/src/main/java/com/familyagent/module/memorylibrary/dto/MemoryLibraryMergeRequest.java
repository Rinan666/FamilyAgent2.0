package com.familyagent.module.memorylibrary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MemoryLibraryMergeRequest {

    @NotNull
    private Long familyId;

    @NotBlank
    private String primaryItemId;

    @NotBlank
    private String secondaryItemId;
}
