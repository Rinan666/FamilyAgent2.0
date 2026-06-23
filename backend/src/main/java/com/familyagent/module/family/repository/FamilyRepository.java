package com.familyagent.module.family.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.family.entity.Family;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Family data access.
 */
@Mapper
public interface FamilyRepository extends BaseMapper<Family> {

    @Select("""
        SELECT id, name, description, avatar_url, invite_code, max_members, created_by, created_at, updated_at
        FROM families
        WHERE invite_code = #{inviteCode}
        """)
    Family findByInviteCode(String inviteCode);

    @Select("""
        <script>
        SELECT id, name, description, avatar_url, invite_code, max_members, created_by, created_at, updated_at
        FROM families
        WHERE id IN
        <foreach collection='ids' item='id' open='(' separator=',' close=')'>
            #{id}
        </foreach>
        ORDER BY created_at DESC
        </script>
        """)
    List<Family> findBasicByIds(@Param("ids") List<Long> ids);

    @Update("""
        UPDATE families
        SET created_by = #{createdBy},
            updated_at = NOW()
        WHERE id = #{familyId}
        """)
    int updateCreatedBy(@Param("familyId") Long familyId, @Param("createdBy") Long createdBy);

    @Select("SELECT COUNT(*) FROM families WHERE created_by = #{userId}")
    int countByCreatedBy(@Param("userId") Long userId);

    @Select("SELECT id FROM families WHERE id = #{familyId} FOR UPDATE")
    Long lockById(@Param("familyId") Long familyId);
}
