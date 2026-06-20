package com.familyagent.module.family.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.familyagent.common.handler.StringArrayTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "family_persona_materials", autoResultMap = true)
public class FamilyPersonaMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;
    private Long personaId;
    private String title;
    private String content;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] tags;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
