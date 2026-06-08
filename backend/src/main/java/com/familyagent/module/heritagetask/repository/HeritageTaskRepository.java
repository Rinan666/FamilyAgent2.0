package com.familyagent.module.heritagetask.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.heritagetask.entity.HeritageTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HeritageTaskRepository extends BaseMapper<HeritageTask> {

    @Select("""
        SELECT * FROM heritage_tasks
        WHERE family_id = #{familyId}
          AND status IN ('PENDING', 'DONE')
        ORDER BY
          CASE status WHEN 'PENDING' THEN 0 ELSE 1 END,
          COALESCE(due_date, CURRENT_DATE + INTERVAL '365 days') ASC,
          created_at DESC
        LIMIT #{limit}
        """)
    List<HeritageTask> findByFamily(@Param("familyId") Long familyId, @Param("limit") int limit);
}
