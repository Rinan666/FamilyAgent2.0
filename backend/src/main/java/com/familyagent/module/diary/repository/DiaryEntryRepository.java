package com.familyagent.module.diary.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.diary.entity.DiaryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DiaryEntryRepository extends BaseMapper<DiaryEntry> {

    @Select("""
        SELECT * FROM diary_entries
        WHERE family_id = #{familyId}
          AND (metadata->>'status' IS NULL OR metadata->>'status' = 'ACTIVE')
          AND (
            user_id = #{viewerUserId}
            OR visibility = 'FAMILY_VISIBLE'
            OR visibility = 'FAMILY'
            OR (
              visibility IN ('CARE_VISIBLE', 'PARENT_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM family_members fm
                WHERE fm.family_id = diary_entries.family_id
                  AND fm.user_id = #{viewerUserId}
                  AND fm.role = 'OWNER'
              )
            )
            OR (
              visibility IN ('CARE_VISIBLE', 'PARENT_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = diary_entries.family_id
                  AND ca.subject_user_id = diary_entries.user_id
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'DIARY')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<DiaryEntry> findVisibleByFamily(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("limit") int limit);

    @Select("""
        SELECT * FROM diary_entries
        WHERE family_id = #{familyId}
          AND user_id = #{userId}
          AND visibility = #{visibility}
          AND (metadata->>'status' IS NULL OR metadata->>'status' = 'ACTIVE')
          AND COALESCE(metadata->>'diaryDate', TO_CHAR(created_at, 'YYYY-MM-DD')) = #{diaryDate}
        ORDER BY created_at ASC
        LIMIT 1
        """)
    DiaryEntry findSameDayMergeCandidate(
            @Param("familyId") Long familyId,
            @Param("userId") Long userId,
            @Param("visibility") String visibility,
            @Param("diaryDate") String diaryDate);

    @Select("""
        SELECT * FROM diary_entries
        WHERE family_id = #{familyId}
          AND user_id = #{targetUserId}
          AND (metadata->>'status' IS NULL OR metadata->>'status' = 'ACTIVE')
          AND (
            user_id = #{viewerUserId}
            OR visibility = 'FAMILY_VISIBLE'
            OR visibility = 'FAMILY'
            OR (
              visibility IN ('CARE_VISIBLE', 'PARENT_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM family_members fm
                WHERE fm.family_id = diary_entries.family_id
                  AND fm.user_id = #{viewerUserId}
                  AND fm.role = 'OWNER'
              )
            )
            OR (
              visibility IN ('CARE_VISIBLE', 'PARENT_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = diary_entries.family_id
                  AND ca.subject_user_id = diary_entries.user_id
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'DIARY')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<DiaryEntry> findVisibleByFamilyAndTarget(
            @Param("familyId") Long familyId,
            @Param("targetUserId") Long targetUserId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("limit") int limit);

    @Select("""
        SELECT * FROM diary_entries
        WHERE family_id = #{familyId}
          AND COALESCE(metadata->>'relatedUserId', '') = CAST(#{targetUserId} AS TEXT)
          AND (metadata->>'status' IS NULL OR metadata->>'status' = 'ACTIVE')
          AND (
            user_id = #{viewerUserId}
            OR visibility = 'FAMILY_VISIBLE'
            OR visibility = 'FAMILY'
            OR (
              visibility IN ('CARE_VISIBLE', 'PARENT_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM family_members fm
                WHERE fm.family_id = diary_entries.family_id
                  AND fm.user_id = #{viewerUserId}
                  AND fm.role = 'OWNER'
              )
            )
            OR (
              visibility IN ('CARE_VISIBLE', 'PARENT_VISIBLE')
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = diary_entries.family_id
                  AND ca.subject_user_id = diary_entries.user_id
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'DIARY')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<DiaryEntry> findVisibleRelatedByFamilyAndTarget(
            @Param("familyId") Long familyId,
            @Param("targetUserId") Long targetUserId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("limit") int limit);

    @Select("""
        SELECT * FROM diary_entries
        WHERE family_id = #{familyId}
          AND (metadata->>'status' IS NULL OR metadata->>'status' = 'ACTIVE')
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<DiaryEntry> findActiveByFamilyForIndexing(
            @Param("familyId") Long familyId,
            @Param("limit") int limit);

    @Select("""
        SELECT * FROM diary_entries
        WHERE family_id = #{familyId}
          AND user_id = #{userId}
          AND (metadata->>'status' IS NULL OR metadata->>'status' = 'ACTIVE')
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<DiaryEntry> findActiveByFamilyAndUserForStyle(
            @Param("familyId") Long familyId,
            @Param("userId") Long userId,
            @Param("limit") int limit);
}
