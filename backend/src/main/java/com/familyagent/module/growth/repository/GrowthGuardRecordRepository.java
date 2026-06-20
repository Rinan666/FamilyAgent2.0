package com.familyagent.module.growth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GrowthGuardRecordRepository extends BaseMapper<GrowthGuardRecord> {

    @Select("""
        SELECT * FROM growth_guard_records
        WHERE family_id = #{familyId}
          AND status = 'ACTIVE'
          AND (
            visibility = 'FAMILY_VISIBLE'
            OR created_by = #{viewerUserId}
            OR target_user_id = #{viewerUserId}
            OR (
              visibility = 'CARE_VISIBLE'
              AND EXISTS (
                SELECT 1 FROM family_members fm
                WHERE fm.family_id = growth_guard_records.family_id
                  AND fm.user_id = #{viewerUserId}
                  AND fm.role = 'OWNER'
              )
            )
            OR (
              visibility = 'CARE_VISIBLE'
              AND target_user_id IS NOT NULL
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = growth_guard_records.family_id
                  AND ca.subject_user_id = growth_guard_records.target_user_id
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'GROWTH_GUARD')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        ORDER BY observed_at DESC, created_at DESC
        LIMIT #{limit}
        """)
    List<GrowthGuardRecord> findVisibleByFamily(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("limit") int limit);

    @Select("""
        SELECT COUNT(*)
        FROM growth_guard_records
        WHERE family_id = #{familyId}
          AND status = 'ACTIVE'
          AND (
            CAST(#{targetUserId} AS BIGINT) IS NULL
            OR target_user_id = CAST(#{targetUserId} AS BIGINT)
          )
          AND (
            CAST(#{keyword} AS VARCHAR) IS NULL
            OR content ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
            OR category ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
          )
          AND (
            visibility = 'FAMILY_VISIBLE'
            OR created_by = #{viewerUserId}
            OR target_user_id = #{viewerUserId}
            OR (
              visibility = 'CARE_VISIBLE'
              AND EXISTS (
                SELECT 1 FROM family_members fm
                WHERE fm.family_id = growth_guard_records.family_id
                  AND fm.user_id = #{viewerUserId}
                  AND fm.role = 'OWNER'
              )
            )
            OR (
              visibility = 'CARE_VISIBLE'
              AND target_user_id IS NOT NULL
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = growth_guard_records.family_id
                  AND ca.subject_user_id = growth_guard_records.target_user_id
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'GROWTH_GUARD')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        """)
    long countVisibleByFamilySearch(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("targetUserId") Long targetUserId,
            @Param("keyword") String keyword);

    @Select("""
        SELECT * FROM growth_guard_records
        WHERE family_id = #{familyId}
          AND status = 'ACTIVE'
          AND (
            CAST(#{targetUserId} AS BIGINT) IS NULL
            OR target_user_id = CAST(#{targetUserId} AS BIGINT)
          )
          AND (
            CAST(#{keyword} AS VARCHAR) IS NULL
            OR content ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
            OR category ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
          )
          AND (
            visibility = 'FAMILY_VISIBLE'
            OR created_by = #{viewerUserId}
            OR target_user_id = #{viewerUserId}
            OR (
              visibility = 'CARE_VISIBLE'
              AND EXISTS (
                SELECT 1 FROM family_members fm
                WHERE fm.family_id = growth_guard_records.family_id
                  AND fm.user_id = #{viewerUserId}
                  AND fm.role = 'OWNER'
              )
            )
            OR (
              visibility = 'CARE_VISIBLE'
              AND target_user_id IS NOT NULL
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = growth_guard_records.family_id
                  AND ca.subject_user_id = growth_guard_records.target_user_id
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'GROWTH_GUARD')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        ORDER BY observed_at DESC, created_at DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<GrowthGuardRecord> searchVisibleByFamily(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("targetUserId") Long targetUserId,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Select("""
        SELECT * FROM growth_guard_records
        WHERE family_id = #{familyId}
          AND status = 'ACTIVE'
        ORDER BY observed_at DESC, created_at DESC
        LIMIT #{limit}
        """)
    List<GrowthGuardRecord> findActiveByFamilyForIndexing(
            @Param("familyId") Long familyId,
            @Param("limit") int limit);

    @Select("""
        SELECT * FROM growth_guard_records
        WHERE family_id = #{familyId}
          AND target_user_id = #{targetUserId}
          AND status = 'ACTIVE'
        ORDER BY observed_at DESC, created_at DESC
        LIMIT #{limit}
        """)
    List<GrowthGuardRecord> findActiveByFamilyAndTargetForStyle(
            @Param("familyId") Long familyId,
            @Param("targetUserId") Long targetUserId,
            @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM growth_guard_records WHERE created_by = #{userId} AND created_at::date = CURRENT_DATE")
    int countTodayByUser(@Param("userId") Long userId);
}
