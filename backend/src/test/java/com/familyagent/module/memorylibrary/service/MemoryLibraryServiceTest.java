package com.familyagent.module.memorylibrary.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.facade.MemoryLibraryFamilyFacade;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.repository.GrowthGuardStalenessVoteRepository;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import com.familyagent.module.memory.facade.MemoryIndexingFacade;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryUpdateRequest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @Mock private MemoryLibraryFamilyFacade familyService;
    @Mock private DiaryEntryRepository diaryEntryRepository;
    @Mock private MemoryEntryRepository memoryEntryRepository;
    @Mock private GrowthGuardRecordRepository growthRecordRepository;
    @Mock private MemoryEntryVoteRepository memoryEntryVoteRepository;
    @Mock private GrowthGuardStalenessVoteRepository growthGuardStalenessVoteRepository;
    @Mock private MemoryIndexingFacade memoryEmbeddingService;
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
            ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> listSql = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class), countArgs.capture());
            verify(jdbcTemplate).query(listSql.capture(), any(org.springframework.jdbc.core.RowMapper.class), listArgs.capture());

            assertTrue(listSql.getValue().contains("jsonb_typeof(me.metadata->'tags')"));
            assertTrue(listSql.getValue().contains("jsonb_typeof(gr.metadata->'tags')"));
            assertPermissionSectionArgs(countArgs.getValue(), false);
            assertPermissionSectionArgs(listArgs.getValue(), true);
            assertEquals(countQuestionMarks(countSql.getValue()), countArgs.getValue().length);
            assertEquals(countQuestionMarks(listSql.getValue()), listArgs.getValue().length);
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
    void search_acceptsLegacyAiSummaryType() {
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
            request.setType("AI_SUMMARY");

            PageResult<MemoryLibraryItem> result = queryService.search(request);

            assertEquals(0L, result.getTotal());
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
        MemoryLibraryMaintenanceService maintenanceService = new MemoryLibraryMaintenanceService(
                familyService, diaryEntryRepository, memoryEntryRepository,
                growthRecordRepository, memoryEmbeddingService, jdbcTemplate);

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
        MemoryLibraryMaintenanceService maintenanceService = new MemoryLibraryMaintenanceService(
                familyService, diaryEntryRepository, memoryEntryRepository,
                growthRecordRepository, memoryEmbeddingService, jdbcTemplate);

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

    @Test
    void deleteArchivedLibraryItem_deletesActiveLegacyAiSummary() {
        MemoryLibraryMaintenanceService maintenanceService = new MemoryLibraryMaintenanceService(
                familyService, diaryEntryRepository, memoryEntryRepository,
                growthRecordRepository, memoryEmbeddingService, jdbcTemplate);

        MemoryEntry entry = new MemoryEntry();
        entry.setId(89L);
        entry.setFamilyId(10L);
        entry.setUserId(101L);
        entry.setStatus("ACTIVE");
        entry.setMetadata(java.util.Map.of("source", MemoryLibrarySupport.LEGACY_AI_SUMMARY_SOURCE));
        when(memoryEntryRepository.selectById(89L)).thenReturn(entry);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);

            maintenanceService.deleteArchivedItem(10L, "memory-89");

            verify(familyService).checkMembership(10L);
            verify(jdbcTemplate).update(
                    "DELETE FROM memory_embeddings WHERE source_type = ? AND source_id = ?",
                    "MEMORY", 89L);
            verify(memoryEntryRepository).deleteById(89L);
        }
    }

    @Test
    void updateLibraryItem_updatesActiveMemoryAndSchedulesReindex() {
        MemoryLibraryMaintenanceService maintenanceService = new MemoryLibraryMaintenanceService(
                familyService, diaryEntryRepository, memoryEntryRepository,
                growthRecordRepository, memoryEmbeddingService, jdbcTemplate);

        MemoryEntry entry = new MemoryEntry();
        entry.setId(88L);
        entry.setFamilyId(10L);
        entry.setUserId(101L);
        entry.setStatus("ACTIVE");
        entry.setType("ELDER_ADVICE");
        entry.setScope("FAMILY_VISIBLE");
        entry.setImportance(3);
        entry.setContent("old content");
        when(memoryEntryRepository.selectById(88L)).thenReturn(entry);

        MemoryLibraryUpdateRequest request = new MemoryLibraryUpdateRequest();
        request.setFamilyId(10L);
        request.setItemId("memory-88");
        request.setTitle("Updated title");
        request.setBody("Updated content");
        request.setType("VALUE");
        request.setVisibility("CARE_VISIBLE");
        request.setTags(List.of("family", "shared"));

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);

            maintenanceService.updateItem(request);

            assertEquals("Updated content", entry.getContent());
            assertEquals("Updated title", entry.getSummary());
            assertEquals("VALUE", entry.getType());
            assertEquals("CARE_VISIBLE", entry.getScope());
            assertEquals(List.of("family", "shared"), ((java.util.Map<?, ?>) entry.getMetadata()).get("tags"));
            verify(memoryEntryRepository).updateById(entry);
            verify(memoryEmbeddingService).indexMemoryAfterCommit(entry);
        }
    }

    @Test
    void updateLibraryItem_rejectsArchivedMemory() {
        MemoryLibraryMaintenanceService maintenanceService = new MemoryLibraryMaintenanceService(
                familyService, diaryEntryRepository, memoryEntryRepository,
                growthRecordRepository, memoryEmbeddingService, jdbcTemplate);

        MemoryEntry entry = new MemoryEntry();
        entry.setId(88L);
        entry.setFamilyId(10L);
        entry.setUserId(101L);
        entry.setStatus("ARCHIVED");
        when(memoryEntryRepository.selectById(88L)).thenReturn(entry);

        MemoryLibraryUpdateRequest request = new MemoryLibraryUpdateRequest();
        request.setFamilyId(10L);
        request.setItemId("memory-88");
        request.setBody("Updated content");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> maintenanceService.updateItem(request));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(memoryEntryRepository, never()).updateById(entry);
        verify(memoryEmbeddingService, never()).indexMemoryAfterCommit(any());
    }

    @Test
    void updateLibraryItem_rejectsNonAuthorEvenWhenFamilyMember() {
        MemoryLibraryMaintenanceService maintenanceService = new MemoryLibraryMaintenanceService(
                familyService, diaryEntryRepository, memoryEntryRepository,
                growthRecordRepository, memoryEmbeddingService, jdbcTemplate);

        MemoryEntry entry = new MemoryEntry();
        entry.setId(88L);
        entry.setFamilyId(10L);
        entry.setUserId(101L);
        entry.setStatus("ACTIVE");
        entry.setType("ELDER_ADVICE");
        entry.setScope("FAMILY_VISIBLE");
        when(memoryEntryRepository.selectById(88L)).thenReturn(entry);

        MemoryLibraryUpdateRequest request = new MemoryLibraryUpdateRequest();
        request.setFamilyId(10L);
        request.setItemId("memory-88");
        request.setBody("Updated content");

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(202L);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> maintenanceService.updateItem(request));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        }

        verify(familyService).checkMembership(10L);
        verify(memoryEntryRepository, never()).updateById(entry);
        verify(memoryEmbeddingService, never()).indexMemoryAfterCommit(any());
    }

    // --- helpers ---

    private static void assertPermissionSectionArgs(Object[] args, boolean includesPagination) {
        int expectedLength = includesPagination ? 57 : 55;
        assertEquals(expectedLength, args.length);
        assertSection(args, 0, 18);
        assertSection(args, 18, 18);
        assertSection(args, 36, 19);
        if (includesPagination) {
            assertEquals(3, args[55]);
            assertEquals(3, args[56]);
        }
    }

    private static void assertSection(Object[] args, int offset, int length) {
        assertEquals(10L, args[offset]);
        assertEquals(101L, args[offset + 1]);
        assertEquals(101L, args[offset + 2]);
        assertEquals(101L, args[offset + 3]);
        if (length == 19) {
            assertEquals(101L, args[offset + 4]);
        }
    }

    private static int countQuestionMarks(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }
}
