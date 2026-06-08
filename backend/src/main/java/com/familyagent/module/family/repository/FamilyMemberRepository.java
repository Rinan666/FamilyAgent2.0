package com.familyagent.module.family.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.family.dto.FamilyMemberVO;
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

    @Select("""
        SELECT id, family_id, user_id, role, joined_at
        FROM family_members
        WHERE family_id = #{familyId}
        """)
    List<FamilyMember> findByFamilyId(Long familyId);

    @Select("""
        SELECT fm.id, fm.family_id AS "familyId", fm.user_id AS "userId",
               u.username, u.nickname, u.avatar_url AS "avatarUrl", fm.role,
               COALESCE(u.metadata->>'birthDate', u.metadata->>'birthday', u.metadata->>'dateOfBirth') AS "birthDate",
               COALESCE(u.metadata->>'birthYear', u.metadata->>'yearOfBirth') AS "birthYear",
               u.metadata AS metadata,
               fm.joined_at AS "joinedAt"
        FROM family_members fm
        JOIN users u ON u.id = fm.user_id
        WHERE fm.family_id = #{familyId}
        ORDER BY fm.joined_at ASC, fm.id ASC
        """)
    List<FamilyMemberVO> findMemberViewsByFamilyId(Long familyId);

    @Select("""
        SELECT fm.id, fm.family_id AS "familyId", fm.user_id AS "userId",
               u.username, u.nickname, u.avatar_url AS "avatarUrl", fm.role,
               COALESCE(u.metadata->>'birthDate', u.metadata->>'birthday', u.metadata->>'dateOfBirth') AS "birthDate",
               COALESCE(u.metadata->>'birthYear', u.metadata->>'yearOfBirth') AS "birthYear",
               u.metadata AS metadata,
               fm.joined_at AS "joinedAt"
        FROM family_members fm
        JOIN users u ON u.id = fm.user_id
        WHERE fm.family_id = #{familyId} AND fm.user_id = #{userId}
        """)
    FamilyMemberVO findMemberViewByFamilyAndUser(Long familyId, Long userId);

    @Select("""
        SELECT id, family_id, user_id, role, joined_at
        FROM family_members
        WHERE user_id = #{userId}
        """)
    List<FamilyMember> findByUserId(Long userId);

    @Select("""
        SELECT id, family_id, user_id, role, joined_at
        FROM family_members
        WHERE family_id = #{familyId} AND user_id = #{userId}
        """)
    FamilyMember findByFamilyAndUser(Long familyId, Long userId);

    @Select("SELECT COUNT(*) FROM family_members WHERE family_id = #{familyId}")
    int countByFamilyId(Long familyId);

    @Delete("DELETE FROM family_members WHERE family_id = #{familyId} AND user_id = #{userId}")
    int removeMember(Long familyId, Long userId);
}
