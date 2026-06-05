package com.familyagent.module.invite.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内测邀请码。
 */
@Data
@TableName("invite_codes")
public class InviteCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String source;
    private String description;
    private Integer maxUses;
    private Integer usedCount;
    private String status;
    private LocalDateTime expiresAt;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
