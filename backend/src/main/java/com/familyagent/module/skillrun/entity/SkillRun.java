package com.familyagent.module.skillrun.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.familyagent.common.handler.PgJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "skill_runs", autoResultMap = true)
public class SkillRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;
    private Long triggeredBy;
    private String skillName;
    private String status;
    private String source;
    private String inputSummary;
    private String outputSummary;
    private Boolean saved;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object usedSources;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
