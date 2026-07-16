package com.familyagent.module.agent.harness.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentToolCallRecordRepository extends BaseMapper<AgentToolCallRecord> {

    default List<AgentToolCallRecord> findByRunId(Long runId) {
        return selectList(Wrappers.<AgentToolCallRecord>lambdaQuery()
                .eq(AgentToolCallRecord::getRunId, runId)
                .orderByAsc(AgentToolCallRecord::getCreatedAt));
    }
}
