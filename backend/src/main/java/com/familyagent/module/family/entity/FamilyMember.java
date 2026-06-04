package com.familyagent.module.family.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家族成员实体
 */
@Data
@TableName(value = "family_members", autoResultMap = true)
public class FamilyMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;
    private Long userId;
    private String role;

    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object permissions;

    private LocalDateTime joinedAt;
}
