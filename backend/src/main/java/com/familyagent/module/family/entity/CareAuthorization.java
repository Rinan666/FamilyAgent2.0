package com.familyagent.module.family.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("care_authorizations")
public class CareAuthorization {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;
    private Long subjectUserId;
    private Long caregiverUserId;
    private String scope;
    private String status;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
