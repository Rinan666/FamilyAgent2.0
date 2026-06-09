package com.familyagent.module.memorylibrary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MemoryLibraryClassicalizeRequest {

    @NotNull
    private Long familyId;

    @NotBlank
    private String itemId;

    @NotBlank
    private String classicalText;

    @NotBlank
    private String plainSummary;

    @NotBlank
    private String styleNote;
}
