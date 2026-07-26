package com.familyagent.module.agent.harness.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentRecordProvenanceRepository extends BaseMapper<AgentRecordProvenance> {

    default AgentRecordProvenance findByRecord(AgentCreatedRecordType recordType, Long memoryEntryId) {
        return selectOne(Wrappers.<AgentRecordProvenance>lambdaQuery()
                .eq(AgentRecordProvenance::getRecordType, recordType.name())
                .eq(AgentRecordProvenance::getMemoryEntryId, memoryEntryId));
    }
}
