package com.familyagent.module.memory.repository;

import com.familyagent.module.memory.entity.MemoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UnifiedDiaryRecordRepository {

    @Select("""
        SELECT * FROM memory_entries
        WHERE origin_type = 'DIARY' AND origin_id = #{diaryId}
        LIMIT 1
        """)
    MemoryEntry findByOriginId(@Param("diaryId") Long diaryId);

    @Select("""
        SELECT * FROM memory_entries
        WHERE family_id = #{familyId}
          AND library_kind = 'FAMILY'
          AND origin_type = 'DIARY'
          AND status = 'ACTIVE'
          AND (
            user_id = #{viewerUserId}
            OR scope = 'FAMILY_VISIBLE'
            OR (scope = 'CARE_VISIBLE' AND EXISTS (
              SELECT 1 FROM family_members fm
              WHERE fm.family_id = memory_entries.family_id
                AND fm.user_id = #{viewerUserId}
                AND fm.role = 'OWNER'
            ))
            OR (scope = 'CARE_VISIBLE' AND EXISTS (
              SELECT 1 FROM care_authorizations ca
              WHERE ca.family_id = memory_entries.family_id
                AND ca.subject_user_id = COALESCE(memory_entries.related_user_id, memory_entries.user_id)
                AND ca.caregiver_user_id = #{viewerUserId}
                AND ca.status = 'ACTIVE'
                AND ca.scope IN ('ALL', 'DIARY')
                AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
            ))
          )
        ORDER BY occurred_at DESC, updated_at DESC
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
          AND origin_type = 'DIARY'
          AND status = 'ACTIVE'
          AND (
            CAST(#{targetUserId} AS BIGINT) IS NULL
            OR user_id = CAST(#{targetUserId} AS BIGINT)
            OR related_user_id = CAST(#{targetUserId} AS BIGINT)
          )
          AND (
            CAST(#{keyword} AS VARCHAR) IS NULL
            OR content ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
            OR COALESCE(title, '') ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
            OR COALESCE(summary, '') ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
          )
          AND (
            user_id = #{viewerUserId}
            OR scope = 'FAMILY_VISIBLE'
            OR (scope = 'CARE_VISIBLE' AND EXISTS (
              SELECT 1 FROM family_members fm
              WHERE fm.family_id = memory_entries.family_id
                AND fm.user_id = #{viewerUserId}
                AND fm.role = 'OWNER'
            ))
            OR (scope = 'CARE_VISIBLE' AND EXISTS (
              SELECT 1 FROM care_authorizations ca
              WHERE ca.family_id = memory_entries.family_id
                AND ca.subject_user_id = COALESCE(memory_entries.related_user_id, memory_entries.user_id)
                AND ca.caregiver_user_id = #{viewerUserId}
                AND ca.status = 'ACTIVE'
                AND ca.scope IN ('ALL', 'DIARY')
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
          AND origin_type = 'DIARY'
          AND status = 'ACTIVE'
          AND (
            CAST(#{targetUserId} AS BIGINT) IS NULL
            OR user_id = CAST(#{targetUserId} AS BIGINT)
            OR related_user_id = CAST(#{targetUserId} AS BIGINT)
          )
          AND (
            CAST(#{keyword} AS VARCHAR) IS NULL
            OR content ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
            OR COALESCE(title, '') ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
            OR COALESCE(summary, '') ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
          )
          AND (
            user_id = #{viewerUserId}
            OR scope = 'FAMILY_VISIBLE'
            OR (scope = 'CARE_VISIBLE' AND EXISTS (
              SELECT 1 FROM family_members fm
              WHERE fm.family_id = memory_entries.family_id
                AND fm.user_id = #{viewerUserId}
                AND fm.role = 'OWNER'
            ))
            OR (scope = 'CARE_VISIBLE' AND EXISTS (
              SELECT 1 FROM care_authorizations ca
              WHERE ca.family_id = memory_entries.family_id
                AND ca.subject_user_id = COALESCE(memory_entries.related_user_id, memory_entries.user_id)
                AND ca.caregiver_user_id = #{viewerUserId}
                AND ca.status = 'ACTIVE'
                AND ca.scope IN ('ALL', 'DIARY')
                AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
            ))
          )
        ORDER BY occurred_at DESC, updated_at DESC
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
        SELECT * FROM memory_entries
        WHERE family_id = #{familyId}
          AND library_kind = 'FAMILY'
          AND origin_type = 'DIARY'
          AND user_id = #{userId}
          AND scope = #{visibility}
          AND status = 'ACTIVE'
          AND COALESCE(metadata->>'diaryDate', TO_CHAR(occurred_at, 'YYYY-MM-DD')) = #{diaryDate}
        ORDER BY occurred_at DESC, updated_at DESC
        LIMIT 2
        """)
    List<MemoryEntry> findSameDayMergeCandidates(
            @Param("familyId") Long familyId,
            @Param("userId") Long userId,
            @Param("visibility") String visibility,
            @Param("diaryDate") String diaryDate);

    @Select("""
        SELECT COUNT(*) FROM memory_entries
        WHERE origin_type = 'DIARY'
          AND user_id = #{userId}
          AND created_at::date = CURRENT_DATE
        """)
    int countTodayByUser(@Param("userId") Long userId);
}
