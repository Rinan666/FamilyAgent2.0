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
          record_id,
          COUNT(*)::int AS stale_votes,
          GREATEST(0.2, 1.0 / (1.0 + COUNT(*) * 0.35)) AS staleness_weight,
          BOOL_OR(user_id = #{viewerUserId}) AS my_voted
        FROM growth_guard_staleness_votes
        WHERE record_id = #{recordId}
        GROUP BY record_id
        """)
    GrowthStalenessStats statsByRecordId(
            @Param("recordId") Long recordId,
            @Param("viewerUserId") Long viewerUserId);
}
