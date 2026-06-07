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
@TableName(value = "growth_guard_records", autoResultMap = true)
public class GrowthGuardRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;
    private Long targetUserId;
    private Long createdBy;
    private String category;
    private String content;
    private Integer severity;
    private LocalDate observedAt;
    private LocalDate followUpAt;
    private String visibility;
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
