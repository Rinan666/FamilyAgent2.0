package com.familyagent.module.mirror.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.mirror.entity.MirrorAgentData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MirrorAgentDataRepository extends BaseMapper<MirrorAgentData> {

    @Select("""
        SELECT id, user_id, primary_family_id, traits, visibility, permission_scope, memory_scope,
               interaction_count, last_updated_at, created_at
        FROM mirror_agent_data
        WHERE primary_family_id = #{familyId}
          AND user_id = #{targetUserId}
          AND (
            user_id = #{viewerUserId}
            OR visibility = 'FAMILY_VISIBLE'
            OR visibility = 'FAMILY'
            OR EXISTS (
              SELECT 1 FROM family_members fm
              WHERE fm.family_id = mirror_agent_data.primary_family_id
                AND fm.user_id = #{viewerUserId}
                AND fm.role = 'OWNER'
            )
            OR (
              visibility = 'CARE_VISIBLE'
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = mirror_agent_data.primary_family_id
                  AND ca.subject_user_id = mirror_agent_data.user_id
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'DIARY')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        """)
    MirrorAgentData findVisibleByFamilyAndTarget(
            @Param("familyId") Long familyId,
            @Param("targetUserId") Long targetUserId,
            @Param("viewerUserId") Long viewerUserId);
}
