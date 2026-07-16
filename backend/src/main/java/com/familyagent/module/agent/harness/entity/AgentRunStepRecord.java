package com.familyagent.module.agent.harness.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_run_steps")
public class AgentRunStepRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;
    private String requestId;
    private String spanId;
    private String parentSpanId;
    private String stepType;
    private String operation;
    private String status;
    private String provider;
    private String model;
    private String promptVersion;
    private String skillVersion;
    private Long latencyMs;
    private String errorCode;
    private Boolean degraded;
    private String privacyCategories;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
