package com.familyagent.module.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.module.agent.harness.dto.AgentTraceObservation;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AgentChatStreamTracker extends FilterOutputStream {

    private static final int MAX_EVENT_LINE_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
    private boolean lineOverflow;
    private boolean done;
    private boolean failed;
    private String errorCode;
    private final List<AgentTraceObservation> traceObservations = new ArrayList<>();

    public AgentChatStreamTracker(OutputStream outputStream, ObjectMapper objectMapper) {
        super(outputStream);
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(int value) throws IOException {
        out.write(value);
        inspect(value);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        out.write(bytes, offset, length);
        for (int index = offset; index < offset + length; index++) {
            inspect(bytes[index]);
        }
    }

    public boolean completedSuccessfully() {
        return done && !failed;
    }

    public boolean failed() {
        return failed;
    }

    public String errorCode() {
        return errorCode;
    }

    public List<AgentTraceObservation> traceObservations() {
        return List.copyOf(traceObservations);
    }

    private void inspect(int value) {
        int unsignedValue = value & 0xff;
        if (unsignedValue == '\n') {
            processLine();
            lineBuffer.reset();
            lineOverflow = false;
            return;
        }
        if (!lineOverflow && lineBuffer.size() < MAX_EVENT_LINE_BYTES) {
            lineBuffer.write(unsignedValue);
            return;
        }
        lineOverflow = true;
    }

    private void processLine() {
        if (lineOverflow || lineBuffer.size() == 0) {
            return;
        }
        String line = lineBuffer.toString(StandardCharsets.UTF_8).trim();
        if (!line.startsWith("data:")) {
            return;
        }
        String payload = line.substring(5).trim();
        if (payload.isEmpty()) {
            return;
        }
        try {
            JsonNode event = objectMapper.readTree(payload);
            JsonNode observations = event.path("traceObservations");
            if (observations.isArray()) {
                observations.forEach(item -> AgentTraceObservation.from(item)
                        .ifPresent(traceObservations::add));
            }
            String type = event.path("type").asText("");
            if ("error".equals(type) || event.path("error").asBoolean(false)) {
                failed = true;
                String code = event.path("code").asText("").trim();
                errorCode = code.isEmpty() ? null : code;
                return;
            }
            if ("done".equals(type) || event.path("done").asBoolean(false)) {
                done = true;
            }
        } catch (IOException ignored) {
            // Malformed upstream events remain a client contract concern.
        }
    }
}
