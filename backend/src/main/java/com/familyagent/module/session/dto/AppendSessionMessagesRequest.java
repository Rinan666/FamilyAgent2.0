package com.familyagent.module.session.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AppendSessionMessagesRequest {

    @Valid
    @NotEmpty(message = "messages 不能为空")
    private List<ChatSessionMessagePayload> messages;
}
