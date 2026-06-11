package com.familyagent.module.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.familyagent.common.handler.PgJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "chat_session_messages", autoResultMap = true)
public class ChatSessionMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Integer seq;
    private String clientMessageId;
    private String role;
    private String content;
    private String toolName;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object metadata;

    private LocalDateTime createdAt;
    private Integer tokenCount;
}
