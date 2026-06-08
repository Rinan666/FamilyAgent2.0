package com.familyagent.module.heritagetask.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
public class CreateHeritageTaskRequest {

    @NotNull
    private Long familyId;

    private Long memoryId;

    @NotBlank
    @Size(max = 100)
    private String title;

    @NotBlank
    private String action;

    @Size(max = 100)
    private String targetLabel;

    private LocalDate dueDate;
    private Map<String, Object> metadata;
}
