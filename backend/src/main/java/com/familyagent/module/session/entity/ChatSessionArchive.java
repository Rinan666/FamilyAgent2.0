package com.familyagent.module.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "chat_session_archives", autoResultMap = true)
public class ChatSessionArchive {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Integer startSeq;
    private Integer endSeq;
    private String summary;
    private String objectKey;
    private Integer messageCount;
    private Integer tokenCount;
    private LocalDateTime createdAt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object metadata;
}
