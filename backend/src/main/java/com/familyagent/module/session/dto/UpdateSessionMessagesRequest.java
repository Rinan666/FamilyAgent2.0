package com.familyagent.module.session.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新会话消息请求
 */
@Data
public class UpdateSessionMessagesRequest {

    @Valid
    @NotNull(message = "消息列表不能为空")
    private List<CreateChatSessionRequest.ChatMessagePayload> messages;
}
