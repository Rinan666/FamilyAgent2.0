package com.familyagent.module.growth.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.constant.CareAuthorizationScope;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.facade.FamilyCareAuthorizationFacade;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link PermissionGate} that aggregates
 * family membership and care-authorization facades
 * behind a single capability interface.
 */
@Service
@RequiredArgsConstructor
public class GrowthGuardPermissionGate implements PermissionGate {

    private final FamilyMembershipFacade familyMembershipFacade;
    private final FamilyCareAuthorizationFacade careAuthorizationFacade;

    @Override
    public void checkMembership(Long familyId) {
        familyMembershipFacade.checkMembership(familyId);
    }

    @Override
    public void checkOwner(Long familyId) {
        familyMembershipFacade.checkOwner(familyId);
    }

    @Override
    public FamilyMember requireFamilyMember(Long familyId, Long userId) {
        return familyMembershipFacade.requireMember(familyId, userId);
    }

    @Override
    public void ensureCanCareForTarget(Long familyId, Long targetUserId, Long viewerUserId) {
        if (!careAuthorizationFacade.canViewScope(
                familyId, targetUserId, viewerUserId,
                CareAuthorizationScope.GROWTH_GUARD.name())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有该成员的照护授权");
        }
    }

    @Override
    public void ensureCanViewRecord(GrowthGuardRecord record, Long viewerUserId) {
        if (viewerUserId.equals(record.getCreatedBy())
                || viewerUserId.equals(record.getTargetUserId())
                || MemoryScope.FAMILY_VISIBLE.name().equalsIgnoreCase(record.getVisibility())) {
            return;
        }
        if (record.getTargetUserId() != null && careAuthorizationFacade.canViewScope(
                record.getFamilyId(),
                record.getTargetUserId(),
                viewerUserId,
                CareAuthorizationScope.GROWTH_GUARD.name())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该成长观察");
    }

    @Override
    public void ensureCanModifyRecord(GrowthGuardRecord record) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        if (viewerUserId.equals(record.getCreatedBy())) {
            return;
        }
        try {
            familyMembershipFacade.checkOwner(record.getFamilyId());
            return;
        } catch (BusinessException ignored) {
            // Fall through to forbidden.
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "只能更新自己创建的记录，或由家族创建者更新");
    }

    @Override
    public void ensureCanArchiveRecord(GrowthGuardRecord record) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        boolean selfCreated = viewerUserId.equals(record.getCreatedBy());
        boolean familyOwner = false;
        try {
            familyMembershipFacade.checkOwner(record.getFamilyId());
            familyOwner = true;
        } catch (BusinessException ignored) {
            // Fall through to creator-only permission.
        }
        if (!selfCreated && !familyOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己创建的记录，或由家族创建者删除");
        }
    }
}
