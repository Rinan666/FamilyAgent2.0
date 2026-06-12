package com.familyagent.module.memorylibrary.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.repository.GrowthGuardReportRepository;
import com.familyagent.module.growth.repository.GrowthGuardStalenessVoteRepository;
import com.familyagent.module.growth.service.GrowthGuardService;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryLibraryServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private FamilyService familyService;
    @Mock private DiaryEntryRepository diaryEntryRepository;
    @Mock private MemoryEntryRepository memoryEntryRepository;
    @Mock private GrowthGuardService growthGuardService;
    @Mock private GrowthGuardRecordRepository growthRecordRepository;
    @Mock private GrowthGuardReportRepository growthReportRepository;
    @Mock private MemoryEntryVoteRepository memoryEntryVoteRepository;
    @Mock private GrowthGuardStalenessVoteRepository growthGuardStalenessVoteRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    // --- MemoryLibraryQueryService ---

    @Test
    void search_checksMembershipAndUsesCurrentViewerForEveryPermissionSection() {
        MemoryLibraryQueryService queryService = new MemoryLibraryQueryService(
                jdbcTemplate, familyService, objectMapper,
                memoryEntryVoteRepository, growthGuardStalenessVoteRepository);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
            when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            MemoryLibrarySearchRequest request = new MemoryLibrarySearchRequest();
            request.setFamilyId(10L);
            request.setKeyword("牙齿");
            request.setType("ALL");
            request.setPage(2);
            request.setPageSize(3);

            PageResult<MemoryLibraryItem> result = queryService.search(request);

            verify(familyService).checkMembership(10L);
            assertEquals(2, result.getPage());
            assertEquals(3, result.getPageSize());

            ArgumentCaptor<Object[]> countArgs = ArgumentCaptor.forClass(Object[].class);
            ArgumentCaptor<Object[]> listArgs = ArgumentCaptor.forClass(Object[].class);
            verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), countArgs.capture());
            verify(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), listArgs.capture());

            assertPermissionSectionArgs(countArgs.getValue(), false);
            assertPermissionSectionArgs(listArgs.getValue(), true);
        }
    }

    @Test
    void search_rejectsUnsupportedTypeBeforeQueryingDatabase() {
        MemoryLibraryQueryService queryService = new MemoryLibraryQueryService(
                jdbcTemplate, familyService, objectMapper,
                memoryEntryVoteRepository, growthGuardStalenessVoteRepository);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            MemoryLibrarySearchRequest request = new MemoryLibrarySearchRequest();
            request.setFamilyId(10L);
            request.setType("PRIVATE_RAW");

            BusinessException exception = assertThrows(BusinessException.class, () -> queryService.search(request));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
            verify(familyService).checkMembership(10L);
        }
    }

    @Test
    void search_requiresFamilyId() {
        MemoryLibraryQueryService queryService = new MemoryLibraryQueryService(
                jdbcTemplate, familyService, objectMapper,
                memoryEntryVoteRepository, growthGuardStalenessVoteRepository);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> queryService.search(new MemoryLibrarySearchRequest()));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
    }

    // --- MemoryLibraryMaintenanceService ---

    @Test
    void deleteArchivedLibraryItem_deletesArchivedMemoryAndEmbeddings() {
        MemoryLibraryQueryService queryService = new MemoryLibraryQueryService(
                jdbcTemplate, familyService, objectMapper,
                memoryEntryVoteRepository, growthGuardStalenessVoteRepository);
        MemoryLibraryMaintenanceService maintenanceService = new MemoryLibraryMaintenanceService(
                familyService, diaryEntryRepository, memoryEntryRepository,
                growthRecordRepository, growthReportRepository, growthGuardService,
                queryService, jdbcTemplate);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            MemoryEntry entry = new MemoryEntry();
            entry.setId(88L);
            entry.setFamilyId(10L);
            entry.setUserId(101L);
            entry.setStatus("ARCHIVED");
            when(memoryEntryRepository.selectById(88L)).thenReturn(entry);

            maintenanceService.deleteArchivedItem(10L, "memory-88");

            verify(familyService).checkMembership(10L);
            verify(jdbcTemplate).update(
                    "DELETE FROM memory_embeddings WHERE source_type = ? AND source_id = ?",
                    "MEMORY", 88L);
            verify(memoryEntryRepository).deleteById(88L);
        }
    }

    @Test
    void deleteArchivedLibraryItem_rejectsActiveMemory() {
        MemoryLibraryQueryService queryService = new MemoryLibraryQueryService(
                jdbcTemplate, familyService, objectMapper,
                memoryEntryVoteRepository, growthGuardStalenessVoteRepository);
        MemoryLibraryMaintenanceService maintenanceService = new MemoryLibraryMaintenanceService(
                familyService, diaryEntryRepository, memoryEntryRepository,
                growthRecordRepository, growthReportRepository, growthGuardService,
                queryService, jdbcTemplate);

        MemoryEntry entry = new MemoryEntry();
        entry.setId(88L);
        entry.setFamilyId(10L);
        entry.setUserId(101L);
        entry.setStatus("ACTIVE");
        when(memoryEntryRepository.selectById(88L)).thenReturn(entry);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> maintenanceService.deleteArchivedItem(10L, "memory-88"));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(familyService).checkMembership(10L);
        verify(memoryEntryRepository, never()).deleteById(88L);
    }

    // --- helpers ---

    private static void assertPermissionSectionArgs(Object[] args, boolean includesPagination) {
        int expectedLength = includesPagination ? 52 : 50;
        assertEquals(expectedLength, args.length);
        assertSection(args, 0, 12);
        assertSection(args, 12, 12);
        assertSection(args, 24, 13);
        assertSection(args, 37, 13);
        if (includesPagination) {
            assertEquals(3, args[50]);
            assertEquals(3, args[51]);
        }
    }

    private static void assertSection(Object[] args, int offset, int length) {
        assertEquals(10L, args[offset]);
        assertEquals(101L, args[offset + 1]);
        assertEquals(101L, args[offset + 2]);
        assertEquals(101L, args[offset + 3]);
        if (length == 13) {
            assertEquals(101L, args[offset + 4]);
        }
    }
}
