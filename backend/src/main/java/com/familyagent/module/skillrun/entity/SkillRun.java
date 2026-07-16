package com.familyagent.module.skillrun.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.familyagent.module.skillrun.dto.SkillRunMetadata;
import com.familyagent.module.skillrun.dto.SkillRunSourceRef;
import com.familyagent.module.skillrun.handler.SkillRunMetadataTypeHandler;
import com.familyagent.module.skillrun.handler.SkillRunSourceListTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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

    @TableField(typeHandler = SkillRunSourceListTypeHandler.class)
    private List<SkillRunSourceRef> usedSources;

    @TableField(typeHandler = SkillRunMetadataTypeHandler.class)
    private SkillRunMetadata metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
