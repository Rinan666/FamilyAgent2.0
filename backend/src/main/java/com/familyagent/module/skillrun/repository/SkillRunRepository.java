package com.familyagent.module.skillrun.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.skillrun.entity.SkillRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SkillRunRepository extends BaseMapper<SkillRun> {

    @Select("""
        SELECT * FROM skill_runs
        WHERE family_id = #{familyId}
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<SkillRun> findByFamily(@Param("familyId") Long familyId, @Param("limit") int limit);
}
