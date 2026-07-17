package com.familyagent.module.agent.harness.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentRecordProvenanceRepository extends BaseMapper<AgentRecordProvenance> {

    default AgentRecordProvenance findByRecord(AgentCreatedRecordType recordType, Long recordId) {
        var query = Wrappers.<AgentRecordProvenance>lambdaQuery()
                .eq(AgentRecordProvenance::getRecordType, recordType.name());
        switch (recordType) {
            case MEMORY_ENTRY -> query.eq(AgentRecordProvenance::getMemoryEntryId, recordId);
            case DIARY_ENTRY -> query.eq(AgentRecordProvenance::getDiaryEntryId, recordId);
            case GROWTH_GUARD_RECORD -> query.eq(AgentRecordProvenance::getGrowthGuardRecordId, recordId);
        }
        return selectOne(query);
    }
}
