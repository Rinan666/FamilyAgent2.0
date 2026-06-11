package com.familyagent.module.heritagetask.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.familyagent.common.handler.PgJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName(value = "heritage_tasks", autoResultMap = true)
public class HeritageTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;
    private Long memoryId;
    private Long createdBy;
    private String title;
    private String action;
    private String targetLabel;
    private LocalDate dueDate;
    private String status;
    private String completionNote;
    private Long completedBy;
    private LocalDateTime completedAt;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
