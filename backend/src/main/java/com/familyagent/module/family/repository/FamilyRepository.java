package com.familyagent.module.family.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.family.entity.Family;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 家族数据访问
 */
@Mapper
public interface FamilyRepository extends BaseMapper<Family> {

    @Select("SELECT * FROM families WHERE invite_code = #{inviteCode}")
    Family findByInviteCode(String inviteCode);
}
