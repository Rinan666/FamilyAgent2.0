package com.familyagent.module.growth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName(value = "growth_guard_reports", autoResultMap = true)
public class GrowthGuardReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;
    private Long targetUserId;
    private Long createdBy;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private String title;
    private String summary;
    private String visibility;
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object report;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
