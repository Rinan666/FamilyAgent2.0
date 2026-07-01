package com.familyagent.module.memorylibrary.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class MemoryLibraryItem {
    private String id;
    private String sourceType;
    private String type;
    private String title;
    private String body;
    private Long familyId;
    private Long authorUserId;
    private Long memberUserId;
    private String memberName;
    private String visibility;
    private String[] tags;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
