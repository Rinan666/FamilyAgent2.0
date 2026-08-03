package com.familyagent.module.growth.service;

import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.growth.entity.GrowthGuardRecord;

/**
 * Capability interface for permission checks used by growth guard operations.
 *
 * <p>Aggregates family membership and care-authorization facades
 * behind a single responsibility boundary so that write/read services depend
 * on "what they can ask" rather than "who answers".</p>
 */
public interface PermissionGate {

    /** Ensures the current user is a member of the given family. */
    void checkMembership(Long familyId);

    /** Ensures the current user is the owner of the given family. */
    void checkOwner(Long familyId);

    /**
     * Finds the family member for the given family and user, throwing
     * {@link com.familyagent.common.exception.BusinessException} with
     * {@code NOT_FAMILY_MEMBER} if absent.
     */
    FamilyMember requireFamilyMember(Long familyId, Long userId);

    /**
     * Ensures {@code viewerUserId} has care authorization to view
     * {@code targetUserId} in the growth-guard scope.
     */
    void ensureCanCareForTarget(Long familyId, Long targetUserId, Long viewerUserId);

    /** Ensures {@code viewerUserId} is allowed to view the given record. */
    void ensureCanViewRecord(GrowthGuardRecord record, Long viewerUserId);

    /** Ensures the current user is allowed to modify the given record. */
    void ensureCanModifyRecord(GrowthGuardRecord record);

    /** Ensures the current user is allowed to archive (soft-delete) the given record. */
    void ensureCanArchiveRecord(GrowthGuardRecord record);
}
