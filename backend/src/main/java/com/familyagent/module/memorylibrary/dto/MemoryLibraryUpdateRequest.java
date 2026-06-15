package com.familyagent.module.memorylibrary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MemoryLibraryUpdateRequest {

    @NotNull
    private Long familyId;

    @NotBlank
    private String itemId;

    @Size(max = 120)
    private String title;

    @NotBlank
    @Size(max = 2000)
    private String body;

    private String type;
    private String visibility;

    @Size(max = 10)
    private List<@Size(max = 32) String> tags;
}
