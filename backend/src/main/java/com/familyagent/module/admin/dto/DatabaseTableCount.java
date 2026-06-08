package com.familyagent.module.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatabaseTableCount {

    private String tableName;
    private String label;
    private long count;
    private boolean legacy;
}
