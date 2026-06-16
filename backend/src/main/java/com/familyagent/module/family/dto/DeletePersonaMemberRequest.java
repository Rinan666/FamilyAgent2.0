package com.familyagent.module.family.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeletePersonaMemberRequest {

    /** The user must type "确认删除" to confirm. */
    @NotBlank
    private String confirmationWord;
}
