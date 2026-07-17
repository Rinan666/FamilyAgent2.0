package com.familyagent.infra.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

public final class AIStreamEventEncoder {

    private AIStreamEventEncoder() {
    }

    public static byte[] encode(ObjectMapper objectMapper, Object event) throws JsonProcessingException {
        return ("data: " + objectMapper.writeValueAsString(event) + "\n\n")
                .getBytes(StandardCharsets.UTF_8);
    }
}
