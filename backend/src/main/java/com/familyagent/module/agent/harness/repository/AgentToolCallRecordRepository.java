package com.familyagent.module.agent.harness.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentToolCallRecordRepository extends BaseMapper<AgentToolCallRecord> {
}
