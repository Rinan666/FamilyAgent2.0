package com.familyagent.module.session.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ChatSessionSummary {

    private Long id;
    private Long userId;
    private Long familyId;
    private String subject;
    private String title;
    private String summary;
    private String status;
    private String visibility;
    private String source;
    private Integer messageCount;
    private Integer tokenCount;
    private LocalDateTime lastMessageAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Map<String, Object> metadata;
}
