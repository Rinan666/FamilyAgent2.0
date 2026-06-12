package com.familyagent.module.session.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PatchChatSessionRequest {
    private Map<String, Object> metadata;
}
