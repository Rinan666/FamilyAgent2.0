package com.familyagent.module.mirror.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "mirror_agent_data", autoResultMap = true)
public class MirrorAgentData {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long primaryFamilyId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> traits;

    private String visibility;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> permissionScope;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> memoryScope;

    private Integer interactionCount;
    private LocalDateTime lastUpdatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
