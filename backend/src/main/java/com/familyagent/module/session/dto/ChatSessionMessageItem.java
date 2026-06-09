package com.familyagent.module.session.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ChatSessionMessageItem {

    private Long seq;
    private String id;
    private String role;
    private String content;
    private String toolName;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private Integer tokenCount;
}
