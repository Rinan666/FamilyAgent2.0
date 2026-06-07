package com.familyagent.module.growth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.growth.entity.GrowthGuardReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GrowthGuardReportRepository extends BaseMapper<GrowthGuardReport> {

    @Select("""
        SELECT * FROM growth_guard_reports
        WHERE family_id = #{familyId}
          AND status = 'ACTIVE'
          AND (
            visibility = 'FAMILY_VISIBLE'
            OR created_by = #{viewerUserId}
            OR target_user_id = #{viewerUserId}
            OR (
              visibility IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM family_members fm
                WHERE fm.family_id = growth_guard_reports.family_id
                  AND fm.user_id = #{viewerUserId}
                  AND fm.role IN ('OWNER', 'ADMIN')
              )
            )
            OR (
              visibility IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
              AND target_user_id IS NOT NULL
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = growth_guard_reports.family_id
                  AND ca.subject_user_id = growth_guard_reports.target_user_id
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'GROWTH_GUARD')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        ORDER BY week_end DESC, created_at DESC
        LIMIT #{limit}
        """)
    List<GrowthGuardReport> findVisibleByFamily(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("limit") int limit);
}
