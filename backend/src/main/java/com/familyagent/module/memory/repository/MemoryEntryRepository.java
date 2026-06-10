package com.familyagent.module.memory.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemoryEntryRepository extends BaseMapper<MemoryEntry> {

    @Select("""
        SELECT * FROM memory_entries
        WHERE user_id = #{userId}
          AND status = 'ACTIVE'
        ORDER BY importance DESC, updated_at DESC
        LIMIT #{limit}
        """)
    List<MemoryEntry> findActiveByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
        SELECT * FROM memory_entries
        WHERE family_id = #{familyId}
          AND status = 'ACTIVE'
          AND type IN ('FAMILY_STORY', 'ELDER_ADVICE', 'HEALTH_REMINDER', 'GROWTH_RISK', 'VALUE')
          AND (
            scope = 'FAMILY_VISIBLE'
            OR user_id = #{viewerUserId}
            OR (
              scope IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM family_members fm
                WHERE fm.family_id = memory_entries.family_id
                  AND fm.user_id = #{viewerUserId}
                  AND fm.role = 'OWNER'
              )
            )
            OR (
              scope IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = memory_entries.family_id
                  AND ca.subject_user_id = memory_entries.user_id
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'DIARY', 'GROWTH_GUARD')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        ORDER BY importance DESC, updated_at DESC
        LIMIT #{limit}
        """)
    List<MemoryEntry> findActiveFamilyMemories(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("limit") int limit);

    @Select("""
        SELECT COUNT(*)
        FROM memory_entries
        WHERE family_id = #{familyId}
          AND status = 'ACTIVE'
          AND type IN ('FAMILY_STORY', 'ELDER_ADVICE', 'HEALTH_REMINDER', 'GROWTH_RISK', 'VALUE')
          AND (
            CAST(#{targetUserId} AS BIGINT) IS NULL
            OR user_id = CAST(#{targetUserId} AS BIGINT)
          )
          AND (
            CAST(#{keyword} AS VARCHAR) IS NULL
            OR content ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
            OR COALESCE(summary, '') ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
            OR COALESCE(metadata->>'scenario', '') ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
          )
          AND (
            scope = 'FAMILY_VISIBLE'
            OR user_id = #{viewerUserId}
            OR (
              scope IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM family_members fm
                WHERE fm.family_id = memory_entries.family_id
                  AND fm.user_id = #{viewerUserId}
                  AND fm.role = 'OWNER'
              )
            )
            OR (
              scope IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = memory_entries.family_id
                  AND ca.subject_user_id = memory_entries.user_id
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'DIARY', 'GROWTH_GUARD')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        """)
    long countActiveFamilyMemoriesSearch(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("targetUserId") Long targetUserId,
            @Param("keyword") String keyword);

    @Select("""
        SELECT * FROM memory_entries
        WHERE family_id = #{familyId}
          AND status = 'ACTIVE'
          AND type IN ('FAMILY_STORY', 'ELDER_ADVICE', 'HEALTH_REMINDER', 'GROWTH_RISK', 'VALUE')
          AND (
            CAST(#{targetUserId} AS BIGINT) IS NULL
            OR user_id = CAST(#{targetUserId} AS BIGINT)
          )
          AND (
            CAST(#{keyword} AS VARCHAR) IS NULL
            OR content ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
            OR COALESCE(summary, '') ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
            OR COALESCE(metadata->>'scenario', '') ILIKE CONCAT('%', CAST(#{keyword} AS VARCHAR), '%')
          )
          AND (
            scope = 'FAMILY_VISIBLE'
            OR user_id = #{viewerUserId}
            OR (
              scope IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM family_members fm
                WHERE fm.family_id = memory_entries.family_id
                  AND fm.user_id = #{viewerUserId}
                  AND fm.role = 'OWNER'
              )
            )
            OR (
              scope IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = memory_entries.family_id
                  AND ca.subject_user_id = memory_entries.user_id
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'DIARY', 'GROWTH_GUARD')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        ORDER BY created_at DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<MemoryEntry> searchActiveFamilyMemories(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("targetUserId") Long targetUserId,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Select("""
        SELECT * FROM memory_entries
        WHERE id = #{memoryId}
          AND family_id = #{familyId}
          AND status = 'ACTIVE'
          AND type IN ('FAMILY_STORY', 'ELDER_ADVICE', 'HEALTH_REMINDER', 'GROWTH_RISK', 'VALUE')
          AND (
            scope = 'FAMILY_VISIBLE'
            OR user_id = #{viewerUserId}
            OR (
              scope IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM family_members fm
                WHERE fm.family_id = memory_entries.family_id
                  AND fm.user_id = #{viewerUserId}
                  AND fm.role = 'OWNER'
              )
            )
            OR (
              scope IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = memory_entries.family_id
                  AND ca.subject_user_id = memory_entries.user_id
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'DIARY', 'GROWTH_GUARD')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        LIMIT 1
        """)
    MemoryEntry findVisibleFamilyMemoryById(
            @Param("familyId") Long familyId,
            @Param("memoryId") Long memoryId,
            @Param("viewerUserId") Long viewerUserId);

    @Select("""
        SELECT * FROM memory_entries
        WHERE family_id = #{familyId}
          AND status = 'ACTIVE'
          AND metadata->>'source' = 'DIARY_PROMOTION'
          AND metadata->>'sourceDiaryId' = #{sourceDiaryId}
        ORDER BY created_at DESC
        LIMIT 1
        """)
    MemoryEntry findActiveBySourceDiaryId(
            @Param("familyId") Long familyId,
            @Param("sourceDiaryId") String sourceDiaryId);

    @Select("""
        SELECT * FROM memory_entries
        WHERE user_id = #{userId}
          AND status = 'ACTIVE'
          AND (CAST(#{subject} AS VARCHAR) IS NULL OR subject IS NULL OR subject = CAST(#{subject} AS VARCHAR))
        ORDER BY
          importance DESC,
          updated_at DESC
        LIMIT #{limit}
        """)
    List<MemoryEntry> recall(
            @Param("userId") Long userId,
            @Param("subject") String subject,
            @Param("limit") int limit);

    @Select("""
        SELECT * FROM memory_entries
        WHERE family_id = #{familyId}
          AND status = 'ACTIVE'
          AND type IN ('FAMILY_STORY', 'ELDER_ADVICE', 'HEALTH_REMINDER', 'GROWTH_RISK', 'VALUE')
        ORDER BY updated_at DESC
        LIMIT #{limit}
        """)
    List<MemoryEntry> findActiveByFamilyForIndexing(
            @Param("familyId") Long familyId,
            @Param("limit") int limit);

    @Select("""
        SELECT * FROM memory_entries
        WHERE family_id = #{familyId}
          AND user_id = #{userId}
          AND status = 'ACTIVE'
        ORDER BY importance DESC, updated_at DESC
        LIMIT #{limit}
        """)
    List<MemoryEntry> findActiveByFamilyAndUserForStyle(
            @Param("familyId") Long familyId,
            @Param("userId") Long userId,
            @Param("limit") int limit);
}
