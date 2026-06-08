package com.familyagent.module.memory.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.memory.dto.MemoryVoteStats;
import com.familyagent.module.memory.entity.MemoryEntryVote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MemoryEntryVoteRepository extends BaseMapper<MemoryEntryVote> {

    @Select("""
        SELECT
          memory_id,
          SUM(CASE WHEN vote_type = 'UP' THEN 1 ELSE 0 END)::int AS up_votes,
          SUM(CASE WHEN vote_type = 'DOWN' THEN 1 ELSE 0 END)::int AS down_votes,
          (
            SUM(CASE WHEN vote_type = 'UP' THEN 1 ELSE 0 END)
            - SUM(CASE WHEN vote_type = 'DOWN' THEN 1 ELSE 0 END)
          )::int AS vote_score,
          GREATEST(0.3, LEAST(4.0, 1.0 + (
            SUM(CASE WHEN vote_type = 'UP' THEN 1 ELSE 0 END)
            - SUM(CASE WHEN vote_type = 'DOWN' THEN 1 ELSE 0 END)
          ) / 3.0)) AS consensus_weight,
          MAX(CASE WHEN user_id = #{viewerUserId} THEN vote_type ELSE NULL END) AS my_vote
        FROM memory_entry_votes
        WHERE memory_id = #{memoryId}
        GROUP BY memory_id
        """)
    MemoryVoteStats statsByMemoryId(
            @Param("memoryId") Long memoryId,
            @Param("viewerUserId") Long viewerUserId);

}
