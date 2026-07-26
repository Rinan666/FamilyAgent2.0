package com.familyagent.module.family.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.family.entity.FamilyRelationship;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FamilyRelationshipRepository extends BaseMapper<FamilyRelationship> {

    @Select("""
        SELECT id, family_id, from_user_id, to_user_id, label, reverse_label, note,
               created_by, updated_by, created_at, updated_at
        FROM family_relationships
        WHERE family_id = #{familyId}
        ORDER BY updated_at DESC, id DESC
        """)
    List<FamilyRelationship> findByFamilyId(Long familyId);

    @Select("""
        SELECT id, family_id, from_user_id, to_user_id, label, reverse_label, note,
               created_by, updated_by, created_at, updated_at
        FROM family_relationships
        WHERE family_id = #{familyId} AND from_user_id = #{fromUserId}
        ORDER BY updated_at DESC, id DESC
        """)
    List<FamilyRelationship> findByFamilyAndViewer(Long familyId, Long fromUserId);

    @Select("""
        SELECT id, family_id, from_user_id, to_user_id, label, reverse_label, note,
               created_by, updated_by, created_at, updated_at
        FROM family_relationships
        WHERE family_id = #{familyId}
          AND from_user_id = #{fromUserId}
          AND to_user_id = #{toUserId}
        """)
    FamilyRelationship findByFamilyViewerAndTarget(Long familyId, Long fromUserId, Long toUserId);
}
