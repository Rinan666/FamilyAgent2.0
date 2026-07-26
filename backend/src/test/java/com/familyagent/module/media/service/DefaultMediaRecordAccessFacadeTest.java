package com.familyagent.module.media.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MediaRecordType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.family.service.CareAuthorizationService;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.service.PermissionGate;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.facade.UnifiedDiaryRecordFacade;
import com.familyagent.module.memory.facade.UnifiedGrowthRecordFacade;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultMediaRecordAccessFacadeTest {

    @Mock private UnifiedDiaryRecordFacade diaryRecords;
    @Mock private UnifiedGrowthRecordFacade growthRecords;
    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private FamilyService familyService;
    @Mock private CareAuthorizationService careAuthorizationService;
    @Mock private PermissionGate growthPermissionGate;
    @InjectMocks private DefaultMediaRecordAccessFacade accessFacade;

    @Test
    void requireWritableDiary_allowsDiaryOwner() {
        DiaryEntry entry = diary(12L, 3L, 42L, "PRIVATE");
        when(diaryRecords.findById(12L)).thenReturn(entry);

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(42L);

            MediaRecordAccess access = accessFacade.requireWritable(MediaRecordType.DIARY, 12L);

            verify(familyService).checkMembership(3L);
            assertEquals(MediaRecordType.DIARY, access.recordType());
            assertEquals(3L, access.familyId());
        }
    }

    @Test
    void requireReadableDiary_rejectsPrivateDiaryForOtherUser() {
        DiaryEntry entry = diary(12L, 3L, 42L, "PRIVATE");
        when(diaryRecords.findById(12L)).thenReturn(entry);

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(43L);

            assertThrows(BusinessException.class, () -> accessFacade.requireReadable(MediaRecordType.DIARY, 12L));
        }
    }

    @Test
    void requireReadableGrowth_usesExistingPermissionGate() {
        GrowthGuardRecord record = growth(12L, 3L, 42L);
        when(growthRecords.findById(12L)).thenReturn(record);

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(43L);

            MediaRecordAccess access = accessFacade.requireReadable(MediaRecordType.GROWTH, 12L);

            verify(growthPermissionGate).checkMembership(3L);
            verify(growthPermissionGate).ensureCanViewRecord(record, 43L);
            assertEquals(MediaRecordType.GROWTH, access.recordType());
        }
    }

    @Test
    void requireReadableMemory_usesExistingVisibleMemoryQuery() {
        MemoryEntry entry = memory(12L, 3L, 42L);
        when(memoryRepository.selectById(12L)).thenReturn(entry);
        when(memoryRepository.findVisibleFamilyMemoryById(3L, 12L, 43L)).thenReturn(entry);

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(43L);

            MediaRecordAccess access = accessFacade.requireReadable(MediaRecordType.MEMORY, 12L);

            verify(familyService).checkMembership(3L);
            assertEquals(MediaRecordType.MEMORY, access.recordType());
            assertEquals(3L, access.familyId());
        }
    }

    private static DiaryEntry diary(Long id, Long familyId, Long userId, String visibility) {
        DiaryEntry entry = new DiaryEntry();
        entry.setId(id);
        entry.setFamilyId(familyId);
        entry.setUserId(userId);
        entry.setVisibility(visibility);
        return entry;
    }

    private static GrowthGuardRecord growth(Long id, Long familyId, Long createdBy) {
        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setId(id);
        record.setFamilyId(familyId);
        record.setCreatedBy(createdBy);
        record.setStatus(EntityStatus.ACTIVE.name());
        return record;
    }

    private static MemoryEntry memory(Long id, Long familyId, Long userId) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setFamilyId(familyId);
        entry.setUserId(userId);
        entry.setStatus(EntityStatus.ACTIVE.name());
        return entry;
    }
}
