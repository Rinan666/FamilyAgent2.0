package com.familyagent.module.family.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertFamilyRelationshipRequest {

    @Size(max = 60, message = "称呼不能超过 60 个字符")
    private String label;

    @Size(max = 60, message = "反向称呼不能超过 60 个字符")
    private String reverseLabel;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String note;
}
