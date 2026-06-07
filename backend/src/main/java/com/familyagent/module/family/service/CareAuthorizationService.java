package com.familyagent.module.family.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.dto.CareAuthorizationVO;
import com.familyagent.module.family.dto.UpsertCareAuthorizationRequest;
import com.familyagent.module.family.entity.CareAuthorization;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.repository.CareAuthorizationRepository;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CareAuthorizationService {

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_DIARY = "DIARY";
    public static final String SCOPE_GROWTH_GUARD = "GROWTH_GUARD";
    private static final Set<String> SCOPES = Set.of(SCOPE_ALL, SCOPE_DIARY, SCOPE_GROWTH_GUARD);

    private final FamilyService familyService;
    private final FamilyMemberRepository memberRepository;
    private final CareAuthorizationRepository authorizationRepository;

    public List<CareAuthorizationVO> listMyCareAuthorizations(Long familyId) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(familyId);
        return authorizationRepository.findRelevantToUser(familyId, viewerUserId)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public CareAuthorizationVO upsertAuthorization(
            Long familyId,
            Long subjectUserId,
            Long caregiverUserId,
            UpsertCareAuthorizationRequest request) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(familyId);
        ensureFamilyMember(familyId, subjectUserId, "被照护成员不属于当前家族");
        ensureFamilyMember(familyId, caregiverUserId, "照护成员不属于当前家族");

        if (subjectUserId.equals(caregiverUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能给自己创建照护授权");
        }

        boolean canManage = viewerUserId.equals(subjectUserId) || isOwnerOrAdmin(familyId, viewerUserId);
        if (!canManage) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION, "只有本人或家庭管理员可以维护照护授权");
        }

        String scope = normalizeScope(request == null ? null : request.getScope());
        boolean active = request == null || request.getActive() == null || request.getActive();
        CareAuthorization authorization = authorizationRepository.findOne(familyId, subjectUserId, caregiverUserId, scope);
        LocalDateTime now = LocalDateTime.now();
        if (authorization == null) {
            authorization = new CareAuthorization();
            authorization.setFamilyId(familyId);
            authorization.setSubjectUserId(subjectUserId);
            authorization.setCaregiverUserId(caregiverUserId);
            authorization.setScope(scope);
            authorization.setCreatedBy(viewerUserId);
            authorization.setCreatedAt(now);
        }
        authorization.setStatus(active ? "ACTIVE" : "REVOKED");
        authorization.setExpiresAt(request == null ? null : request.getExpiresAt());
        authorization.setUpdatedBy(viewerUserId);
        authorization.setUpdatedAt(now);

        if (authorization.getId() == null) {
            authorizationRepository.insert(authorization);
        } else {
            authorizationRepository.updateById(authorization);
        }
        return toVO(authorization);
    }

    public boolean canViewCareScope(Long familyId, Long subjectUserId, Long viewerUserId, String scope) {
        if (subjectUserId == null || viewerUserId == null) {
            return false;
        }
        if (subjectUserId.equals(viewerUserId)) {
            return true;
        }
        if (isOwnerOrAdmin(familyId, viewerUserId)) {
            return true;
        }
        return authorizationRepository.hasActiveAuthorization(
                familyId,
                subjectUserId,
                viewerUserId,
                normalizeScope(scope));
    }

    private void ensureFamilyMember(Long familyId, Long userId, String message) {
        FamilyMember member = memberRepository.findByFamilyAndUser(familyId, userId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER, message);
        }
    }

    private boolean isOwnerOrAdmin(Long familyId, Long userId) {
        FamilyMember member = memberRepository.findByFamilyAndUser(familyId, userId);
        return member != null && ("OWNER".equalsIgnoreCase(member.getRole()) || "ADMIN".equalsIgnoreCase(member.getRole()));
    }

    private String normalizeScope(String scope) {
        String normalized = scope == null || scope.isBlank()
                ? SCOPE_GROWTH_GUARD
                : scope.trim().toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "照护授权范围不支持");
        }
        return normalized;
    }

    private CareAuthorizationVO toVO(CareAuthorization authorization) {
        return CareAuthorizationVO.builder()
                .id(authorization.getId())
                .familyId(authorization.getFamilyId())
                .subjectUserId(authorization.getSubjectUserId())
                .caregiverUserId(authorization.getCaregiverUserId())
                .scope(authorization.getScope())
                .status(authorization.getStatus())
                .expiresAt(authorization.getExpiresAt())
                .createdAt(authorization.getCreatedAt())
                .updatedAt(authorization.getUpdatedAt())
                .build();
    }
}
