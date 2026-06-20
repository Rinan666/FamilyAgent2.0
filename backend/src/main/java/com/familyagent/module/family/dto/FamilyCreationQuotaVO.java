package com.familyagent.module.family.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Family creation quota for the current user.
 */
@Data
@Builder
public class FamilyCreationQuotaVO {

    private int maxFamilies;

    private int createdFamilies;

    private int remainingFamilies;
}
