package com.familyagent.module.session.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Create chat session request.
 */
@Data
public class CreateChatSessionRequest {

    private Long familyId;
    private Long questionId;
    private String subject;
    private Long knowledgePointId;
    private String title;
    private String summary;
    private List<ChatSessionMessagePayload> messages;
    private String visibility;
    private Map<String, Object> permissionScope;
    private String source;
    private Map<String, Object> metadata;
}
