package com.familyagent.module.session.service;

import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.entity.ChatSessionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionArchiveSummaryService {

    private final AIServiceClient aiServiceClient;

    public Map<String, Object> summarize(ChatSession session, List<ChatSessionMessage> messages) {
        try {
            Map<String, Object> response = aiServiceClient.summarizeSessionArchive(Map.of(
                    "session_id", session.getId(),
                    "session_title", session.getTitle() == null ? "" : session.getTitle(),
                    "family_id", session.getFamilyId() == null ? 0 : session.getFamilyId(),
                    "subject", session.getSubject() == null ? "" : session.getSubject(),
                    "messages", messages.stream().map(message -> Map.of(
                            "seq", message.getSeq(),
                            "role", message.getRole(),
                            "content", message.getContent(),
                            "created_at", message.getCreatedAt() == null ? "" : message.getCreatedAt().toString()
                    )).toList()
            ));
            Object data = response == null ? null : response.get("data");
            if (data instanceof Map<?, ?> map) {
                return ChatSessionSupportUtils.castMap(map);
            }
        } catch (Exception error) {
            log.warn("Session archive AI summary failed: sessionId={}, error={}", session.getId(), error.getMessage());
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("summary", fallbackSummary(messages));
        fallback.put("titleSuggestion", session.getTitle() == null ? fallbackTitle(messages) : session.getTitle());
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
