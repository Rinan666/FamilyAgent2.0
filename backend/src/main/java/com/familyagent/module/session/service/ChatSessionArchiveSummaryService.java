package com.familyagent.module.session.service;

import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.entity.ChatSessionMessage;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatSessionArchiveSummaryService {

    public Map<String, Object> summarize(ChatSession session, List<ChatSessionMessage> messages) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("summary", fallbackSummary(messages));
        fallback.put("titleSuggestion", session.getTitle() == null ? fallbackTitle(messages) : session.getTitle());
        fallback.put("focusTopics", List.of("family_chat"));
        fallback.put("confidence", "FALLBACK");
        return fallback;
    }

    private static String fallbackSummary(List<ChatSessionMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index -= 1) {
            String content = messages.get(index).getContent();
            if (content != null && !content.isBlank()) {
                return content.trim().substring(0, Math.min(content.trim().length(), 120));
            }
        }
        return "会话归档片段";
    }

    private static String fallbackTitle(List<ChatSessionMessage> messages) {
        for (ChatSessionMessage message : messages) {
            if ("user".equalsIgnoreCase(message.getRole()) && message.getContent() != null && !message.getContent().isBlank()) {
                return message.getContent().trim().substring(0, Math.min(message.getContent().trim().length(), 36));
            }
        }
        return "会话归档";
    }
}
