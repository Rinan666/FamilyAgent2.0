package com.familyagent.module.photo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.familyagent.common.handler.PgJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "photos", autoResultMap = true)
public class Photo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;
    private Long uploaderId;
    private String objectKey;
    private String scope;
    private String mimeType;
    private Long fileSize;
    private String originalName;
    private String description;
    private LocalDateTime takenAt;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
