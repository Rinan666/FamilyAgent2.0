package com.familyagent.module.agent.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AgentDraftRequestIdFactory {

    private static final int REQUEST_ID_LIMIT = 128;

    public String create(String prefix, String value) {
        if (value == null || value.isBlank()) {
            return prefix + "-" + UUID.randomUUID();
        }
        String text = value.trim();
        return text.length() <= REQUEST_ID_LIMIT ? text : text.substring(0, REQUEST_ID_LIMIT);
    }
}
