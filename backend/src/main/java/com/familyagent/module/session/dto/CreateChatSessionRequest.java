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
    private String subject;
    private String title;
    private String summary;
    private List<ChatSessionMessagePayload> messages;
    private String visibility;
    private Map<String, Object> permissionScope;
    private String source;
    private String agentContextType;
    private Long targetUserId;
    private Long targetPersonaId;
    private Map<String, Object> metadata;
}
