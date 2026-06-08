package com.familyagent.module.heritagetask.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompleteHeritageTaskRequest {

    @Size(max = 2000)
    private String completionNote;
}
