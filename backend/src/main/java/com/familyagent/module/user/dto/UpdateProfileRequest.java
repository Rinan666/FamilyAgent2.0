package com.familyagent.module.user.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "Birth date must use the YYYY-MM-DD format")
    private String birthDate;
}
