package com.familyagent.module.agent.harness.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.agent.harness.entity.AgentToolConfirmationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentToolConfirmationRecordRepository extends BaseMapper<AgentToolConfirmationRecord> {

    @Select("SELECT * FROM agent_tool_confirmations WHERE id = #{id} FOR UPDATE")
    AgentToolConfirmationRecord selectByIdForUpdate(Long id);
}
