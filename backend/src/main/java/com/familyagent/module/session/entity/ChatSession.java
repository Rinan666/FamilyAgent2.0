package com.familyagent.module.session.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家教会话实体
 */
@Data
@TableName(value = "chat_sessions", autoResultMap = true)
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long familyId;
    private Long questionId;
    private String subject;
    private Long knowledgePointId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object messages;

    private String summary;
    private String status;
    private String visibility;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object permissionScope;

    private String source;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;
}
