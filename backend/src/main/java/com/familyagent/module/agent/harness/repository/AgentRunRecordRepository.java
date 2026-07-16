package com.familyagent.module.agent.harness.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.familyagent.module.agent.harness.entity.AgentRunRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentRunRecordRepository extends BaseMapper<AgentRunRecord> {

    default List<AgentRunRecord> findByRequestId(String requestId) {
        return selectList(Wrappers.<AgentRunRecord>lambdaQuery()
                .eq(AgentRunRecord::getRequestId, requestId)
                .orderByDesc(AgentRunRecord::getCreatedAt));
    }

    default List<AgentRunRecord> findBySessionId(Long sessionId) {
        return selectList(Wrappers.<AgentRunRecord>lambdaQuery()
                .eq(AgentRunRecord::getSessionId, sessionId)
                .orderByDesc(AgentRunRecord::getCreatedAt));
    }
}
