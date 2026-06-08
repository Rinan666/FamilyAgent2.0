package com.familyagent.module.user.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "出生日期格式应为 YYYY-MM-DD")
    private String birthDate;
}
