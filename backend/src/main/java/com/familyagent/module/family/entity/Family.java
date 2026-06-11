package com.familyagent.module.family.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.familyagent.common.handler.PgJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Family entity.
 */
@Data
@TableName(value = "families", autoResultMap = true)
public class Family {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;
    private String avatarUrl;
    private String inviteCode;
    private Integer maxMembers;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object settings;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
