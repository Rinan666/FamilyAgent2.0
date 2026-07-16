package com.familyagent.module.agent.harness.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_runs")
public class AgentRunRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;
    private Long familyId;
    private Long viewerUserId;
    private Long sessionId;
    private String agentMode;
    private String subject;
    private String contextLabel;
    private String status;
    private String errorCode;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
