package com.familyagent.module.agent.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatStreamRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_shouldRejectBlankMessage() {
        AgentChatStreamRequest request = new AgentChatStreamRequest();
        request.setMemberMessage(" ");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void validate_shouldRejectTooManyHistoryMessages() {
        AgentChatStreamRequest request = new AgentChatStreamRequest();
        request.setMemberMessage("hello");
        request.setHistory(List.of(
                new AgentChatStreamRequest.HistoryMessage(), new AgentChatStreamRequest.HistoryMessage(),
                new AgentChatStreamRequest.HistoryMessage(), new AgentChatStreamRequest.HistoryMessage(),
                new AgentChatStreamRequest.HistoryMessage(), new AgentChatStreamRequest.HistoryMessage(),
                new AgentChatStreamRequest.HistoryMessage(), new AgentChatStreamRequest.HistoryMessage(),
                new AgentChatStreamRequest.HistoryMessage(), new AgentChatStreamRequest.HistoryMessage(),
                new AgentChatStreamRequest.HistoryMessage(), new AgentChatStreamRequest.HistoryMessage(),
                new AgentChatStreamRequest.HistoryMessage(), new AgentChatStreamRequest.HistoryMessage(),
                new AgentChatStreamRequest.HistoryMessage(), new AgentChatStreamRequest.HistoryMessage(),
                new AgentChatStreamRequest.HistoryMessage(), new AgentChatStreamRequest.HistoryMessage(),
                new AgentChatStreamRequest.HistoryMessage(), new AgentChatStreamRequest.HistoryMessage(),
                new AgentChatStreamRequest.HistoryMessage()
        ));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void toAiPayload_shouldOnlyExposeWhitelistedFields() {
        AgentChatStreamRequest request = new AgentChatStreamRequest();
        request.setMemberMessage(" hello ");
        request.setSubject("Math");
        AgentChatStreamRequest.HistoryMessage history = new AgentChatStreamRequest.HistoryMessage();
        history.setRole("assistant");
        history.setContent("previous");
        request.setHistory(List.of(history));

        Map<String, Object> payload = request.toAiPayload();

        assertEquals("hello", payload.get("member_message"));
        assertEquals("Math", payload.get("subject"));
        assertTrue(payload.containsKey("history"));
        assertFalse(payload.containsKey("unknown"));
    }
}
