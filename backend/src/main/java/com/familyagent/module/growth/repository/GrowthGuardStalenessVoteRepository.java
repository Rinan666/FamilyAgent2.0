package com.familyagent.module.growth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.growth.dto.GrowthStalenessStats;
import com.familyagent.module.growth.entity.GrowthGuardStalenessVote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GrowthGuardStalenessVoteRepository extends BaseMapper<GrowthGuardStalenessVote> {

    @Select("""
        SELECT
          me.origin_id AS record_id,
          COUNT(*)::int AS stale_votes,
          GREATEST(0.2, 1.0 / (1.0 + COUNT(*) * 0.35)) AS staleness_weight,
          BOOL_OR(v.user_id = #{viewerUserId}) AS my_voted
        FROM growth_guard_staleness_votes v
        JOIN memory_entries me
          ON me.id = v.memory_entry_id
         AND me.origin_type = 'GROWTH'
        WHERE me.origin_id = #{recordId}
        GROUP BY me.origin_id
        """)
    GrowthStalenessStats statsByRecordId(
            @Param("recordId") Long recordId,
            @Param("viewerUserId") Long viewerUserId);
}
