package com.familyagent.module.memory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
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
    private String subject;
    private String type;
    private String scope;
    private String content;
    private String summary;
    private Integer importance;
    private BigDecimal confidence;
    private Long sourceSessionId;
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
