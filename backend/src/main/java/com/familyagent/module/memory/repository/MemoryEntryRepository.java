package com.familyagent.module.memory.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemoryEntryRepository extends BaseMapper<MemoryEntry> {

    @Select("""
        SELECT * FROM memory_entries
        WHERE user_id = #{userId}
          AND status = 'ACTIVE'
        ORDER BY importance DESC, updated_at DESC
        LIMIT #{limit}
        """)
    List<MemoryEntry> findActiveByUserId(Long userId, int limit);

    @Select("""
        SELECT * FROM memory_entries
        WHERE user_id = #{userId}
          AND status = 'ACTIVE'
          AND (#{subject} IS NULL OR subject IS NULL OR subject = #{subject})
          AND (#{knowledgePointId} IS NULL OR knowledge_point_id IS NULL OR knowledge_point_id = #{knowledgePointId})
        ORDER BY
          CASE WHEN #{knowledgePointId} IS NOT NULL AND knowledge_point_id = #{knowledgePointId} THEN 0 ELSE 1 END,
          importance DESC,
          updated_at DESC
        LIMIT #{limit}
        """)
    List<MemoryEntry> recall(Long userId, String subject, Long knowledgePointId, int limit);
}
