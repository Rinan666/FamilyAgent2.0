package com.familyagent.module.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SuspiciousFamilySummary {

    private Long familyId;
    private String familyName;
    private long memberCount;
    private long ownerCount;
}
