package com.familyagent.module.family.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.family.entity.FamilyPersonaMaterial;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FamilyPersonaMaterialRepository extends BaseMapper<FamilyPersonaMaterial> {

    @Select("""
            SELECT *
            FROM family_persona_materials
            WHERE family_id = #{familyId}
              AND persona_id = #{personaId}
            ORDER BY updated_at DESC, id DESC
            """)
    List<FamilyPersonaMaterial> findByPersonaId(Long familyId, Long personaId);

    @Select("""
            SELECT *
            FROM family_persona_materials
            WHERE id = #{id}
              AND family_id = #{familyId}
              AND persona_id = #{personaId}
            """)
    FamilyPersonaMaterial findByIdAndPersonaId(Long id, Long familyId, Long personaId);

    @Select("""
            SELECT COUNT(*)
            FROM family_persona_materials
            WHERE family_id = #{familyId}
              AND persona_id = #{personaId}
            """)
    int countByPersonaId(Long familyId, Long personaId);

    @Delete("DELETE FROM family_persona_materials WHERE family_id = #{familyId} AND persona_id = #{personaId}")
    int deleteByPersonaId(Long familyId, Long personaId);
}
