package com.familyagent.module.agent.harness.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.familyagent.module.agent.harness.entity.AgentRunStepRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentRunStepRecordRepository extends BaseMapper<AgentRunStepRecord> {

    default List<AgentRunStepRecord> findByRunId(Long runId) {
        return selectList(Wrappers.<AgentRunStepRecord>lambdaQuery()
                .eq(AgentRunStepRecord::getRunId, runId)
                .orderByAsc(AgentRunStepRecord::getCreatedAt));
    }
}
