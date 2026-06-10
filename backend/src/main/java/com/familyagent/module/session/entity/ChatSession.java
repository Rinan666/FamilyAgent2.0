package com.familyagent.module.session.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Chat session entity.
 */
@Data
@TableName(value = "chat_sessions", autoResultMap = true)
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long familyId;
    private String subject;

    @Deprecated
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object messages;

    private String title;
    private String summary;
    private String status;
    private String visibility;
    private LocalDateTime lastMessageAt;
    private Integer messageCount;
    private Integer tokenCount;
    private Integer archivedBeforeSeq;
    private String archiveStatus;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object permissionScope;

    private String source;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object metadata;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object archiveMetadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;
}
