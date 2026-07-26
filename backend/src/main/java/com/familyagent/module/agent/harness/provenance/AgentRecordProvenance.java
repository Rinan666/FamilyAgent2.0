package com.familyagent.module.agent.harness.provenance;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_record_provenance")
public class AgentRecordProvenance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;
    private Long agentRunId;
    private Long toolCallId;
    private String toolName;
    private String toolVersion;
    private String recordType;
    private Long memoryEntryId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
