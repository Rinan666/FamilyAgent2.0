package com.familyagent.module.media.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.CareAuthorizationScope;
import com.familyagent.common.constant.MediaRecordType;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.family.facade.FamilyCareAuthorizationFacade;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.GrowthRecordPermissionFacade;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.facade.MediaMemoryRecordFacade;
import com.familyagent.module.memory.facade.UnifiedDiaryRecordFacade;
import com.familyagent.module.memory.facade.UnifiedGrowthRecordFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DefaultMediaRecordAccessFacade implements MediaRecordAccessFacade {

    private final UnifiedDiaryRecordFacade diaryRecords;
    private final UnifiedGrowthRecordFacade growthRecords;
    private final MediaMemoryRecordFacade memoryRecords;
    private final FamilyMembershipFacade familyMembership;
    private final FamilyCareAuthorizationFacade careAuthorizationFacade;
    private final GrowthRecordPermissionFacade growthPermissionFacade;

    @Override
    public MediaRecordAccess requireReadable(MediaRecordType recordType, Long recordId) {
        return switch (recordType) {
            case DIARY -> requireReadableDiary(recordId);
            case GROWTH -> requireReadableGrowth(recordId);
            case MEMORY -> requireReadableMemory(recordId);
        };
    }

    @Override
    public MediaRecordAccess requireWritable(MediaRecordType recordType, Long recordId) {
        return switch (recordType) {
            case DIARY -> requireWritableDiary(recordId);
            case GROWTH -> requireWritableGrowth(recordId);
            case MEMORY -> requireWritableMemory(recordId);
        };
    }

    private MediaRecordAccess requireReadableDiary(Long recordId) {
        DiaryEntry entry = requireActiveDiary(recordId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        familyMembership.checkMembership(entry.getFamilyId());
        if (viewerUserId.equals(entry.getUserId())
                || isFamilyVisible(entry.getVisibility())
                || canViewCareDiary(entry, viewerUserId)) {
            return access(MediaRecordType.DIARY, recordId, entry.getFamilyId());
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "No permission to view this diary attachment");
    }

    private MediaRecordAccess requireWritableDiary(Long recordId) {
        DiaryEntry entry = requireActiveDiary(recordId);
        CurrentUserGuard.requireSelf(entry.getUserId());
        familyMembership.checkMembership(entry.getFamilyId());
        return access(MediaRecordType.DIARY, recordId, entry.getFamilyId());
    }

    private MediaRecordAccess requireReadableGrowth(Long recordId) {
        GrowthGuardRecord record = requireActiveGrowth(recordId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        growthPermissionFacade.checkMembership(record.getFamilyId());
        growthPermissionFacade.ensureCanView(record, viewerUserId);
        return access(MediaRecordType.GROWTH, recordId, record.getFamilyId());
    }

    private MediaRecordAccess requireWritableGrowth(Long recordId) {
        GrowthGuardRecord record = requireActiveGrowth(recordId);
        growthPermissionFacade.checkMembership(record.getFamilyId());
        growthPermissionFacade.ensureCanModify(record);
        return access(MediaRecordType.GROWTH, recordId, record.getFamilyId());
    }

    private MediaRecordAccess requireReadableMemory(Long recordId) {
        MemoryEntry entry = requireActiveMemory(recordId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        familyMembership.checkMembership(entry.getFamilyId());
        MemoryEntry visible = memoryRecords.findVisibleById(
                entry.getFamilyId(),
                recordId,
                viewerUserId);
        if (visible == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No permission to view this memory attachment");
        }
        return access(MediaRecordType.MEMORY, recordId, entry.getFamilyId());
    }

    private MediaRecordAccess requireWritableMemory(Long recordId) {
        MemoryEntry entry = requireActiveMemory(recordId);
        CurrentUserGuard.requireSelf(entry.getUserId());
        familyMembership.checkMembership(entry.getFamilyId());
        return access(MediaRecordType.MEMORY, recordId, entry.getFamilyId());
    }

    private DiaryEntry requireActiveDiary(Long recordId) {
        DiaryEntry entry = diaryRecords.findById(recordId);
        if (entry == null || isArchived(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return entry;
    }

    private GrowthGuardRecord requireActiveGrowth(Long recordId) {
        GrowthGuardRecord record = growthRecords.findById(recordId);
        if (record == null || !EntityStatus.ACTIVE.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return record;
    }

    private MemoryEntry requireActiveMemory(Long recordId) {
        MemoryEntry entry = memoryRecords.findById(recordId);
        if (entry == null || !EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return entry;
    }

    private boolean canViewCareDiary(DiaryEntry entry, Long viewerUserId) {
        if (!isCareVisible(entry.getVisibility())) {
            return false;
        }
        return careAuthorizationFacade.canViewScope(
                entry.getFamilyId(),
                entry.getUserId(),
                viewerUserId,
                CareAuthorizationScope.DIARY.name());
    }

    private static MediaRecordAccess access(MediaRecordType recordType, Long recordId, Long familyId) {
        return new MediaRecordAccess(recordType, recordId, familyId);
    }

    private static boolean isFamilyVisible(String visibility) {
        return MemoryScope.FAMILY_VISIBLE.name().equalsIgnoreCase(visibility) || "FAMILY".equalsIgnoreCase(visibility);
    }

    private static boolean isCareVisible(String visibility) {
        return MemoryScope.CARE_VISIBLE.name().equalsIgnoreCase(visibility);
    }

    private static boolean isArchived(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return EntityStatus.ARCHIVED.name().equalsIgnoreCase(String.valueOf(map.get("status")));
        }
        return false;
    }
}
