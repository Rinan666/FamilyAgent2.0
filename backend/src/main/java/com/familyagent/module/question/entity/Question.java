package com.familyagent.module.question.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目实体
 */
@Data
@TableName(value = "questions", autoResultMap = true)
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kpId;
    private String subject;
    private String grade;
    private String type;
    private Integer difficulty;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object answer;

    private String tags;
    private String source;
    private Integer usageCount;
    private Double correctRate;
    private String status;
    private Long createdBy;
    private Long reviewedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
