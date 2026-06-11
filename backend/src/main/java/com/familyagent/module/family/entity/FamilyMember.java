package com.familyagent.module.family.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.familyagent.common.handler.PgJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Family member entity.
 */
@Data
@TableName(value = "family_members", autoResultMap = true)
public class FamilyMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;
    private Long userId;
    private String role;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object permissions;

    private LocalDateTime joinedAt;
}
