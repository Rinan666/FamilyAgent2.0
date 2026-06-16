package com.familyagent.module.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePersonaMemberRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @Size(max = 200)
    private String eraIdentity;

    @Size(max = 1000)
    private String values;

    @Size(max = 1000)
    private String speakingStyle;

    @Size(max = 1000)
    private String personality;
}
