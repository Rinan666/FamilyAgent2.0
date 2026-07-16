package com.familyagent.module.agent.harness.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_confirmations")
public class AgentToolConfirmationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toolName;
    private Long runId;
    private Long familyId;
    private Long viewerUserId;
    private String requestId;
    private Long sessionId;
    private String agentMode;
    private String subject;
    private String contextLabel;
    private Boolean completeRunAfterTool;
    private String idempotencyKey;
    private String inputSummary;
    private String inputPayload;
    private String status;
    private String executionStatus;
    private String executionErrorCode;
    private LocalDateTime expiresAt;
    private LocalDateTime decidedAt;
    private LocalDateTime executedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
