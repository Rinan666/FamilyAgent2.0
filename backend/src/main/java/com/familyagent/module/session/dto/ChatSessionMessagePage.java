package com.familyagent.module.session.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatSessionMessagePage {

    private List<ChatSessionMessageItem> items;
    private boolean hasMore;
    private Long nextBeforeSeq;
}
