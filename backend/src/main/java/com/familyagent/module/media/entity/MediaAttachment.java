package com.familyagent.module.media.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_attachments")
public class MediaAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uploaderId;
    private Long familyId;
    private String objectKey;
    private String mimeType;
    private Long fileSize;
    private String originalName;
    private String recordType;
    private Long recordId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
