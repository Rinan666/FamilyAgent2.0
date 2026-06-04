package com.familyagent.module.question.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识点实体
 */
@Data
@TableName(value = "knowledge_points", autoResultMap = true)
public class KnowledgePoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;
    private String subject;
    private String grade;
    private String name;
    private String description;
    private Integer level;
    private Integer sortOrder;

    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
