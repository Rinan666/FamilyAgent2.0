package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentToolConfirmationPayloadCodec {

    private final ObjectMapper objectMapper;

    public String encode(AgentToolDescriptor descriptor, Object input) {
        if (descriptor == null || input == null || !descriptor.inputType().isInstance(input)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent tool confirmation payload type is invalid");
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to encode Agent tool confirmation payload");
        }
    }

    public <I> I decode(AgentTool<I, ?> tool, String payload) {
        if (tool == null || payload == null || payload.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent tool confirmation payload is missing");
        }
        try {
            return objectMapper.readValue(payload, tool.inputType());
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent tool confirmation payload is invalid");
        }
    }
}
