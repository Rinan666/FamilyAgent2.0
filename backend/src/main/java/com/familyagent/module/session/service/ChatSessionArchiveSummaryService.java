package com.familyagent.module.session.service;

import com.familyagent.module.session.dto.ChatSessionArchiveSummaryData;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.entity.ChatSessionMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatSessionArchiveSummaryService {

    public ChatSessionArchiveSummaryData summarize(ChatSession session, List<ChatSessionMessage> messages) {
        return new ChatSessionArchiveSummaryData(
                fallbackSummary(messages),
                session.getTitle() == null ? fallbackTitle(messages) : session.getTitle(),
                List.of("family_chat"),
                "FALLBACK");
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
