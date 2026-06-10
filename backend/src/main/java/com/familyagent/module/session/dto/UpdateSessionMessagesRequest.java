package com.familyagent.module.session.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Update-session-messages request.
 */
@Data
public class UpdateSessionMessagesRequest {

    @Valid
    @NotNull(message = "娑堟伅鍒楄〃涓嶈兘涓虹┖")
    private List<ChatSessionMessagePayload> messages;
}
