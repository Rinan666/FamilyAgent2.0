package com.familyagent.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.familyagent.common.handler.PgJsonbTypeHandler;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User entity.
 */
@Data
@TableName(value = "users", autoResultMap = true)
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String wechatOpenId;

    @TableField(select = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;

    private String nickname;
    private String avatarUrl;
    private String email;
    private String phone;

    private String role;
    private String status;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object metadata;

    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
