package com.familyagent.module.agent.harness.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_calls")
public class AgentToolCallRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toolName;
    private Long familyId;
    private Long viewerUserId;
    private Long confirmationId;
    private String requestId;
    private String inputSummary;
    private String status;
    private String errorCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
