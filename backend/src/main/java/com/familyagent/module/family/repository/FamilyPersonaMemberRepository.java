package com.familyagent.module.family.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.family.entity.FamilyPersonaMember;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FamilyPersonaMemberRepository extends BaseMapper<FamilyPersonaMember> {

    @Select("SELECT * FROM family_persona_members WHERE family_id = #{familyId} ORDER BY created_at")
    List<FamilyPersonaMember> findByFamilyId(Long familyId);

    @Select("SELECT COUNT(*) FROM family_persona_members WHERE family_id = #{familyId}")
    int countByFamilyId(Long familyId);

    @Select("SELECT * FROM family_persona_members WHERE id = #{id} AND family_id = #{familyId}")
    FamilyPersonaMember findByIdAndFamilyId(Long id, Long familyId);

    @Delete("DELETE FROM family_persona_members WHERE id = #{id} AND family_id = #{familyId}")
    int deleteByIdAndFamilyId(Long id, Long familyId);
}
