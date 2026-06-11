package com.familyagent.module.diary.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.familyagent.common.handler.PgJsonbTypeHandler;
import com.familyagent.common.handler.StringArrayTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "diary_entries", autoResultMap = true)
public class DiaryEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long familyId;
    private String rawText;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object structured;

    private String mood;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] tags;

    private String privacyLevel;
    private String visibility;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object permissionScope;

    private String source;
    private String voiceUrl;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
