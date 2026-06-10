package com.familyagent.module.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Create-family request.
 */
@Data
public class CreateFamilyRequest {

    @NotBlank(message = "家族名称不能为空")
    @Size(min = 2, max = 100, message = "家族名称长度2-100个字符")
    private String name;

    @Size(max = 500, message = "描述不能超过500字符")
    private String description;
}
