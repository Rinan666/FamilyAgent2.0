package com.familyagent.module.assessment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.assessment.entity.AbilityProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 学力档案数据访问
 */
@Mapper
public interface AbilityProfileRepository extends BaseMapper<AbilityProfile> {

    @Select("SELECT * FROM ability_profiles WHERE user_id = #{userId}")
    List<AbilityProfile> findByUserId(Long userId);

    @Select("SELECT * FROM ability_profiles WHERE user_id = #{userId} AND kp_id = #{kpId}")
    AbilityProfile findByUserAndKp(Long userId, Long kpId);

    @Select("""
        SELECT * FROM ability_profiles
        WHERE user_id = #{userId} AND mastery_probability BETWEEN 0.3 AND 0.7
        ORDER BY mastery_probability ASC
        LIMIT #{limit}
        """)
    List<AbilityProfile> findZPD(Long userId, int limit);
}
