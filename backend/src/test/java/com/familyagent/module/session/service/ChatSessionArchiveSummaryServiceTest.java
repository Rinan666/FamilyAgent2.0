package com.familyagent.module.session.service;

import com.familyagent.module.session.dto.ChatSessionArchiveSummaryData;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.entity.ChatSessionMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatSessionArchiveSummaryServiceTest {

    private final ChatSessionArchiveSummaryService service = new ChatSessionArchiveSummaryService();

    @Test
    void summarize_shouldReturnTypedFallbackContract() {
        ChatSession session = new ChatSession();
        session.setTitle("Family planning");
        ChatSessionMessage message = new ChatSessionMessage();
        message.setRole("assistant");
        message.setContent("A concise archive summary.");

        ChatSessionArchiveSummaryData result = service.summarize(session, List.of(message));

        assertEquals("A concise archive summary.", result.summary());
        assertEquals("Family planning", result.titleSuggestion());
        assertEquals(List.of("family_chat"), result.focusTopics());
        assertEquals("FALLBACK", result.confidence());
        assertEquals(Map.of(
                "summary", "A concise archive summary.",
                "titleSuggestion", "Family planning",
                "focusTopics", List.of("family_chat"),
                "confidence", "FALLBACK"), result.toMetadataMap());
    }

    @Test
    void summarize_shouldDeriveTitleFromFirstUserMessage() {
        ChatSession session = new ChatSession();
        ChatSessionMessage assistant = message("assistant", "Answer");
        ChatSessionMessage user = message("user", "Discuss next week's family trip");

        ChatSessionArchiveSummaryData result = service.summarize(session, List.of(assistant, user));

        assertEquals("Discuss next week's family trip", result.summary());
        assertEquals("Discuss next week's family trip", result.titleSuggestion());
    }

    private static ChatSessionMessage message(String role, String content) {
        ChatSessionMessage message = new ChatSessionMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
