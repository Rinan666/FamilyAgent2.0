package com.familyagent.module.session.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建家教会话请求
 */
@Data
public class CreateChatSessionRequest {

    private Long familyId;
    private Long questionId;
    private String subject;
    private Long knowledgePointId;
    private List<ChatMessagePayload> messages;
    private String visibility;
    private Map<String, Object> permissionScope;
    private String source;
    private Map<String, Object> metadata;

    @Data
    public static class ChatMessagePayload {
        @Size(max = 32)
        private String id;

        @Size(max = 20)
        private String role;

        private String content;

        @Size(max = 64)
        private String timestamp;
    }
}
