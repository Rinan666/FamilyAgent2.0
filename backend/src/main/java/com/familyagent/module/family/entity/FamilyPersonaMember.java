package com.familyagent.module.family.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("family_persona_members")
public class FamilyPersonaMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;
    private String name;
    private String description;
    private String eraIdentity;
    private String values;
    private String speakingStyle;
    private String personality;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
