package com.familyagent.module.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.module.agent.harness.constant.AgentRunStepType;
import com.familyagent.module.agent.harness.constant.AgentTracePrivacyCategory;
import com.familyagent.module.agent.harness.dto.AgentTraceObservation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatStreamTrackerTest {

    @Test
    void trackerForwardsBytesAndRecognizesDoneEventAcrossWrites() throws Exception {
        ByteArrayOutputStream downstream = new ByteArrayOutputStream();
        AgentChatStreamTracker tracker = new AgentChatStreamTracker(downstream, new ObjectMapper());

        tracker.write("data: {\"type\":\"content\",\"content\":\"hello\"}\n".getBytes(StandardCharsets.UTF_8));
        tracker.write("data: {\"type\":\"done\",\"done\":true}\n\n".getBytes(StandardCharsets.UTF_8));

        assertTrue(tracker.completedSuccessfully());
        assertFalse(tracker.failed());
        assertTrue(downstream.toString(StandardCharsets.UTF_8).contains("hello"));
    }

    @Test
    void trackerPreservesStructuredStreamErrorCode() throws Exception {
        ByteArrayOutputStream downstream = new ByteArrayOutputStream();
        AgentChatStreamTracker tracker = new AgentChatStreamTracker(downstream, new ObjectMapper());

        tracker.write(("data: {\"type\":\"error\",\"error\":true,"
                + "\"code\":\"AI_PROVIDER_UNAVAILABLE\"}\n\n").getBytes(StandardCharsets.UTF_8));

        assertTrue(tracker.failed());
        assertFalse(tracker.completedSuccessfully());
        assertEquals("AI_PROVIDER_UNAVAILABLE", tracker.errorCode());
    }

    @Test
    void trackerExtractsValidTerminalTraceObservationsAndIgnoresUnknownTypes() throws Exception {
        ByteArrayOutputStream downstream = new ByteArrayOutputStream();
        AgentChatStreamTracker tracker = new AgentChatStreamTracker(downstream, new ObjectMapper());

        tracker.write(("data: {\"type\":\"done\",\"done\":true,\"traceObservations\":["
                + "{\"stepType\":\"LLM\",\"operation\":\"llm.chat_stream\","
                + "\"provider\":\"dashscope\",\"model\":\"dashscope/qwen-flash\","
                + "\"latencyMs\":12,\"success\":true,\"degraded\":false,"
                + "\"privacyCategories\":[\"FAMILY_DATA\"]},"
                + "{\"stepType\":\"WEB_SEARCH\",\"operation\":\"web_search.public\","
                + "\"provider\":\"tavily\",\"latencyMs\":-5,\"success\":false,"
                + "\"errorCode\":\"WEB_SEARCH_PROVIDER_ERROR\",\"degraded\":true,"
                + "\"privacyCategories\":[\"PUBLIC_DATA\",\"UNKNOWN\"]},"
                + "{\"stepType\":\"UNKNOWN\",\"operation\":\"ignored\",\"success\":true}"
                + "]}\n\n").getBytes(StandardCharsets.UTF_8));

        assertTrue(tracker.completedSuccessfully());
        assertEquals(2, tracker.traceObservations().size());
        AgentTraceObservation llm = tracker.traceObservations().get(0);
        assertEquals(AgentRunStepType.LLM, llm.stepType());
        assertEquals("dashscope/qwen-flash", llm.model());
        assertEquals(12L, llm.latencyMs());
        assertEquals(java.util.List.of(AgentTracePrivacyCategory.FAMILY_DATA), llm.privacyCategories());
        AgentTraceObservation search = tracker.traceObservations().get(1);
        assertEquals(AgentRunStepType.WEB_SEARCH, search.stepType());
        assertEquals(0L, search.latencyMs());
        assertEquals("WEB_SEARCH_PROVIDER_ERROR", search.errorCode());
        assertEquals(java.util.List.of(AgentTracePrivacyCategory.PUBLIC_DATA), search.privacyCategories());
    }
}
