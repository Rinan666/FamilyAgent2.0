package com.familyagent.module.memory.repository;

import com.familyagent.module.memory.entity.MemoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UnifiedGrowthRecordRepository {

    @Select("""
        SELECT * FROM memory_entries
        WHERE origin_type = 'GROWTH' AND origin_id = #{recordId}
        LIMIT 1
        """)
    MemoryEntry findByOriginId(@Param("recordId") Long recordId);

    @Select("""
        SELECT * FROM memory_entries
        WHERE family_id = #{familyId}
          AND library_kind = 'FAMILY'
          AND origin_type = 'GROWTH'
          AND status = 'ACTIVE'
          AND (
            scope = 'FAMILY_VISIBLE'
            OR user_id = #{viewerUserId}
            OR related_user_id = #{viewerUserId}
            OR (scope = 'CARE_VISIBLE' AND EXISTS (
              SELECT 1 FROM family_members fm
              WHERE fm.family_id = memory_entries.family_id
                AND fm.user_id = #{viewerUserId}
                AND fm.role = 'OWNER'
            ))
            OR (scope = 'CARE_VISIBLE' AND related_user_id IS NOT NULL AND EXISTS (
              SELECT 1 FROM care_authorizations ca
              WHERE ca.family_id = memory_entries.family_id
                AND ca.subject_user_id = memory_entries.related_user_id
                AND ca.caregiver_user_id = #{viewerUserId}
                AND ca.status = 'ACTIVE'
                AND ca.scope IN ('ALL', 'GROWTH_GUARD')
                AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
            ))
          )
        ORDER BY occurred_at DESC, created_at DESC
        LIMIT #{limit}
        """)
    List<MemoryEntry> findVisibleByFamily(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("limit") int limit);

    @Select("""
        SELECT COUNT(*) FROM memory_entries
        WHERE family_id = #{familyId}
          AND library_kind = 'FAMILY'
          AND origin_type = 'GROWTH'
          AND status = 'ACTIVE'
          AND (CAST(#{targetUserId} AS BIGINT) IS NULL OR related_user_id = CAST(#{targetUserId} AS BIGINT))
          AND (
            CAST(#{keyword} AS VARCHAR) IS NULL
            OR content ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
            OR COALESCE(metadata->'legacyGrowth'->>'category', '') ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
          )
          AND (
            scope = 'FAMILY_VISIBLE'
            OR user_id = #{viewerUserId}
            OR related_user_id = #{viewerUserId}
            OR (scope = 'CARE_VISIBLE' AND EXISTS (
              SELECT 1 FROM family_members fm
              WHERE fm.family_id = memory_entries.family_id
                AND fm.user_id = #{viewerUserId}
                AND fm.role = 'OWNER'
            ))
            OR (scope = 'CARE_VISIBLE' AND related_user_id IS NOT NULL AND EXISTS (
              SELECT 1 FROM care_authorizations ca
              WHERE ca.family_id = memory_entries.family_id
                AND ca.subject_user_id = memory_entries.related_user_id
                AND ca.caregiver_user_id = #{viewerUserId}
                AND ca.status = 'ACTIVE'
                AND ca.scope IN ('ALL', 'GROWTH_GUARD')
                AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
            ))
          )
        """)
    long countVisibleByFamilySearch(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("targetUserId") Long targetUserId,
            @Param("keyword") String keyword);

    @Select("""
        SELECT * FROM memory_entries
        WHERE family_id = #{familyId}
          AND library_kind = 'FAMILY'
          AND origin_type = 'GROWTH'
          AND status = 'ACTIVE'
          AND (CAST(#{targetUserId} AS BIGINT) IS NULL OR related_user_id = CAST(#{targetUserId} AS BIGINT))
          AND (
            CAST(#{keyword} AS VARCHAR) IS NULL
            OR content ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
            OR COALESCE(metadata->'legacyGrowth'->>'category', '') ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
          )
          AND (
            scope = 'FAMILY_VISIBLE'
            OR user_id = #{viewerUserId}
            OR related_user_id = #{viewerUserId}
            OR (scope = 'CARE_VISIBLE' AND EXISTS (
              SELECT 1 FROM family_members fm
              WHERE fm.family_id = memory_entries.family_id
                AND fm.user_id = #{viewerUserId}
                AND fm.role = 'OWNER'
            ))
            OR (scope = 'CARE_VISIBLE' AND related_user_id IS NOT NULL AND EXISTS (
              SELECT 1 FROM care_authorizations ca
              WHERE ca.family_id = memory_entries.family_id
                AND ca.subject_user_id = memory_entries.related_user_id
                AND ca.caregiver_user_id = #{viewerUserId}
                AND ca.status = 'ACTIVE'
                AND ca.scope IN ('ALL', 'GROWTH_GUARD')
                AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
            ))
          )
        ORDER BY occurred_at DESC, created_at DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<MemoryEntry> searchVisibleByFamily(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("targetUserId") Long targetUserId,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Select("""
        SELECT COUNT(*) FROM memory_entries
        WHERE origin_type = 'GROWTH'
          AND user_id = #{userId}
          AND created_at::date = CURRENT_DATE
        """)
    int countTodayByUser(@Param("userId") Long userId);
}
