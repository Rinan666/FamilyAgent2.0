package com.familyagent.module.family.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Confirm-family-deletion request.
 */
@Data
public class DeleteFamilyRequest {

    @NotBlank(message = "Confirmation family name is required")
    @Size(max = 100, message = "Confirmation family name is too long")
    private String confirmationName;

    @AssertTrue(message = "deleteAllData must be true")
    private boolean deleteAllData;
}
