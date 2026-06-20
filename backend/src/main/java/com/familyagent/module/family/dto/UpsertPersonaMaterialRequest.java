package com.familyagent.module.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpsertPersonaMaterialRequest {

    @NotBlank
    @Size(max = 100)
    private String title;

    @NotBlank
    @Size(max = 5000)
    private String content;

    @Size(max = 12)
    private List<@Size(max = 24) String> tags;
}
