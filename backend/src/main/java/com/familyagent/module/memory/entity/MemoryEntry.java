package com.familyagent.module.memory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.familyagent.common.handler.PgJsonbTypeHandler;
import com.familyagent.common.handler.StringArrayTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "memory_entries", autoResultMap = true)
public class MemoryEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long familyId;
    private String libraryKind;
    private String title;
    private Long relatedUserId;
    private String subject;
    private String type;
    private String scope;
    private String content;
    private String summary;
    private Integer importance;
    private BigDecimal confidence;
    private Long sourceSessionId;
    private String status;
    private LocalDateTime occurredAt;
    private String originType;
    private Long originId;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] tags;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
