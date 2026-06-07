package com.familyagent.module.family.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.family.entity.CareAuthorization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CareAuthorizationRepository extends BaseMapper<CareAuthorization> {

    @Select("""
        SELECT id, family_id, subject_user_id, caregiver_user_id, scope, status,
               created_by, updated_by, expires_at, created_at, updated_at
        FROM care_authorizations
        WHERE family_id = #{familyId}
          AND subject_user_id = #{subjectUserId}
          AND caregiver_user_id = #{caregiverUserId}
          AND scope = #{scope}
        """)
    CareAuthorization findOne(
            @Param("familyId") Long familyId,
            @Param("subjectUserId") Long subjectUserId,
            @Param("caregiverUserId") Long caregiverUserId,
            @Param("scope") String scope);

    @Select("""
        SELECT id, family_id, subject_user_id, caregiver_user_id, scope, status,
               created_by, updated_by, expires_at, created_at, updated_at
        FROM care_authorizations
        WHERE family_id = #{familyId}
          AND (subject_user_id = #{userId} OR caregiver_user_id = #{userId})
        ORDER BY updated_at DESC, id DESC
        """)
    List<CareAuthorization> findRelevantToUser(
            @Param("familyId") Long familyId,
            @Param("userId") Long userId);

    @Select("""
        SELECT COUNT(*) > 0
        FROM care_authorizations
        WHERE family_id = #{familyId}
          AND subject_user_id = #{subjectUserId}
          AND caregiver_user_id = #{caregiverUserId}
          AND status = 'ACTIVE'
          AND scope IN ('ALL', #{scope})
          AND (expires_at IS NULL OR expires_at > NOW())
        """)
    boolean hasActiveAuthorization(
            @Param("familyId") Long familyId,
            @Param("subjectUserId") Long subjectUserId,
            @Param("caregiverUserId") Long caregiverUserId,
            @Param("scope") String scope);
}
