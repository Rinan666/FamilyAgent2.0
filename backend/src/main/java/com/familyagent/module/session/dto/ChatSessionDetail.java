package com.familyagent.module.session.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ChatSessionDetail {

    private Long id;
    private Long userId;
    private Long familyId;
    private Long questionId;
    private String subject;
    private Long knowledgePointId;
    private String title;
    private String summary;
    private String status;
    private String visibility;
    private String source;
    private Integer messageCount;
    private Integer tokenCount;
    private Integer archivedBeforeSeq;
    private String archiveStatus;
    private LocalDateTime lastMessageAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Map<String, Object> metadata;
    private Map<String, Object> archiveMetadata;
    private List<ChatSessionArchiveSummary> archives;
}
