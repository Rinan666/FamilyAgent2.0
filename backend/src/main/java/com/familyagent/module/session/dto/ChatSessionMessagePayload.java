package com.familyagent.module.session.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ChatSessionMessagePayload {

    @Size(max = 64)
    private String id;

    @Size(max = 20)
    private String role;

    @Size(max = 12000)
    private String content;

    @Size(max = 64)
    private String timestamp;

    @Size(max = 80)
    private String toolName;

    private Integer tokenCount;
    private Map<String, Object> metadata;
}
