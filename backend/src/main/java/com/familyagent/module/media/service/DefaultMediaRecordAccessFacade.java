package com.familyagent.module.media.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MediaRecordType;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.CareAuthorizationService;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.service.PermissionGate;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DefaultMediaRecordAccessFacade implements MediaRecordAccessFacade {

    private final DiaryEntryRepository diaryRepository;
    private final GrowthGuardRecordRepository growthRepository;
    private final MemoryEntryRepository memoryRepository;
    private final FamilyService familyService;
    private final CareAuthorizationService careAuthorizationService;
    private final PermissionGate growthPermissionGate;

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
        familyService.checkMembership(entry.getFamilyId());
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
        familyService.checkMembership(entry.getFamilyId());
        return access(MediaRecordType.DIARY, recordId, entry.getFamilyId());
    }

    private MediaRecordAccess requireReadableGrowth(Long recordId) {
        GrowthGuardRecord record = requireActiveGrowth(recordId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        growthPermissionGate.checkMembership(record.getFamilyId());
        growthPermissionGate.ensureCanViewRecord(record, viewerUserId);
        return access(MediaRecordType.GROWTH, recordId, record.getFamilyId());
    }

    private MediaRecordAccess requireWritableGrowth(Long recordId) {
        GrowthGuardRecord record = requireActiveGrowth(recordId);
        growthPermissionGate.checkMembership(record.getFamilyId());
        growthPermissionGate.ensureCanModifyRecord(record);
        return access(MediaRecordType.GROWTH, recordId, record.getFamilyId());
    }

    private MediaRecordAccess requireReadableMemory(Long recordId) {
        MemoryEntry entry = requireActiveMemory(recordId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(entry.getFamilyId());
        MemoryEntry visible = memoryRepository.findVisibleFamilyMemoryById(
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
        familyService.checkMembership(entry.getFamilyId());
        return access(MediaRecordType.MEMORY, recordId, entry.getFamilyId());
    }

    private DiaryEntry requireActiveDiary(Long recordId) {
        DiaryEntry entry = diaryRepository.selectById(recordId);
        if (entry == null || isArchived(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return entry;
    }

    private GrowthGuardRecord requireActiveGrowth(Long recordId) {
        GrowthGuardRecord record = growthRepository.selectById(recordId);
        if (record == null || !EntityStatus.ACTIVE.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return record;
    }

    private MemoryEntry requireActiveMemory(Long recordId) {
        MemoryEntry entry = memoryRepository.selectById(recordId);
        if (entry == null || !EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return entry;
    }

    private boolean canViewCareDiary(DiaryEntry entry, Long viewerUserId) {
        if (!isCareVisible(entry.getVisibility())) {
            return false;
        }
        return careAuthorizationService.canViewCareScope(
                entry.getFamilyId(),
                entry.getUserId(),
                viewerUserId,
                CareAuthorizationService.SCOPE_DIARY);
    }

    private static MediaRecordAccess access(MediaRecordType recordType, Long recordId, Long familyId) {
        return new MediaRecordAccess(recordType, recordId, familyId);
    }

    private static boolean isFamilyVisible(String visibility) {
        return MemoryScope.FAMILY_VISIBLE.name().equalsIgnoreCase(visibility) || "FAMILY".equalsIgnoreCase(visibility);
    }

    private static boolean isCareVisible(String visibility) {
        return MemoryScope.CARE_VISIBLE.name().equalsIgnoreCase(visibility)
                || MemoryScope.PARENT_VISIBLE.name().equalsIgnoreCase(visibility);
    }

    private static boolean isArchived(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return EntityStatus.ARCHIVED.name().equalsIgnoreCase(String.valueOf(map.get("status")));
        }
        return false;
    }
}
