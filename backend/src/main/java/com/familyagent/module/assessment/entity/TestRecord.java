package com.familyagent.module.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试记录实体
 */
@Data
@TableName(value = "test_records", autoResultMap = true)
public class TestRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long familyId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object questionIds;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object answers;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object scores;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object timeSpent;
    private Double totalScore;
    private Integer totalTime;
    private String status;
    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
