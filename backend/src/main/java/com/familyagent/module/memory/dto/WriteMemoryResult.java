package com.familyagent.module.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WriteMemoryResult {
    private String savedRecordType;
    private Long savedRecordId;
    private String writeCategory;
    private String visibility;
    private String title;
}
