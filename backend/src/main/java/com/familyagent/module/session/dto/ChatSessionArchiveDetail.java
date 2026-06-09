package com.familyagent.module.session.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ChatSessionArchiveDetail {

    private Long id;
    private Long sessionId;
    private Integer startSeq;
    private Integer endSeq;
    private String summary;
    private String objectKey;
    private Integer messageCount;
    private Integer tokenCount;
    private LocalDateTime createdAt;
    private Map<String, Object> metadata;
    private List<ChatSessionMessageItem> transcript;
}
