package com.familyagent.module.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WriteMemoryResult {
    private String memoryLibrary;
    private Long memoryId;
    private String memoryType;
    private String visibility;
    private String title;
}
