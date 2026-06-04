package com.familyagent.module.family.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.family.entity.FamilyMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 家族成员数据访问
 */
@Mapper
public interface FamilyMemberRepository extends BaseMapper<FamilyMember> {

    @Select("SELECT * FROM family_members WHERE family_id = #{familyId}")
    List<FamilyMember> findByFamilyId(Long familyId);

    @Select("SELECT * FROM family_members WHERE user_id = #{userId}")
    List<FamilyMember> findByUserId(Long userId);

    @Select("SELECT * FROM family_members WHERE family_id = #{familyId} AND user_id = #{userId}")
    FamilyMember findByFamilyAndUser(Long familyId, Long userId);

    @Select("SELECT COUNT(*) FROM family_members WHERE family_id = #{familyId}")
    int countByFamilyId(Long familyId);

    @Delete("DELETE FROM family_members WHERE family_id = #{familyId} AND user_id = #{userId}")
    int removeMember(Long familyId, Long userId);
}
