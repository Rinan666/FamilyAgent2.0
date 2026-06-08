package com.familyagent.module.memory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MemoryVoteRequest {

    @NotBlank
    private String voteType;
}
