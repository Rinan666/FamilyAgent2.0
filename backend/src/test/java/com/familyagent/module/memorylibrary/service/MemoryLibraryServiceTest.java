package com.familyagent.module.memorylibrary.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.module.diary.facade.MemoryLibraryDiaryFacade;
import com.familyagent.module.family.facade.MemoryLibraryFamilyFacade;
import com.familyagent.module.growth.facade.GrowthStalenessQueryFacade;
import com.familyagent.module.growth.facade.MemoryLibraryGrowthFacade;
import com.familyagent.module.growth.dto.GrowthStalenessStats;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.facade.MemoryIndexingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryEmbeddingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryIndexMetadataFacade;
import com.familyagent.module.memory.facade.MemoryLibraryMemoryFacade;
import com.familyagent.module.memory.facade.MemoryLibraryVoteFacade;
import com.familyagent.module.memory.dto.MemoryVoteStats;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryLibraryServiceTest {

    @Mock private MemoryLibraryQueryGateway queryGateway;
    @Mock private MemoryLibraryFamilyFacade familyService;
    @Mock private MemoryLibraryDiaryFacade diaryFacade;
    @Mock private MemoryLibraryMemoryFacade memoryEntryRepository;
    @Mock private MemoryLibraryGrowthFacade growthFacade;
    @Mock private MemoryLibraryVoteFacade memoryVoteFacade;
    @Mock private GrowthStalenessQueryFacade growthStalenessFacade;
    @Mock private MemoryIndexingFacade memoryEmbeddingService;
    @Mock private MemoryLibraryEmbeddingFacade embeddingFacade;
    private final MemoryLibraryIndexMetadataFacade metadataFacade = new MemoryLibraryIndexMetadataFacade();

    // --- MemoryLibraryQueryService ---

    @Test
    void search_checksMembershipAndUsesCurrentViewerForEveryPermissionSection() {
        MemoryLibraryQueryService queryService = new MemoryLibraryQueryService(
                queryGateway, familyService,
                memoryVoteFacade, growthStalenessFacade);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            when(queryGateway.query(any())).thenReturn(
                    new MemoryLibraryQueryGateway.QueryResult(List.of(), 0L));

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

            ArgumentCaptor<MemoryLibraryQueryGateway.QueryCriteria> criteriaCaptor =
                    ArgumentCaptor.forClass(MemoryLibraryQueryGateway.QueryCriteria.class);
            verify(queryGateway).query(criteriaCaptor.capture());
            MemoryLibraryQueryGateway.QueryCriteria criteria = criteriaCaptor.getValue();
            assertEquals(10L, criteria.familyId());
            assertEquals(101L, criteria.viewerUserId());
            assertEquals("ALL", criteria.type());
            assertEquals(3, criteria.limit());
            assertEquals(3, criteria.offset());
            assertEquals(MemoryLibrarySupport.searchTerms(request.getKeyword()), criteria.searchTerms());
        }
    }

    @Test
    void search_rejectsUnsupportedTypeBeforeQueryingDatabase() {
        MemoryLibraryQueryService queryService = new MemoryLibraryQueryService(
                queryGateway, familyService,
                memoryVoteFacade, growthStalenessFacade);

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
                queryGateway, familyService,
                memoryVoteFacade, growthStalenessFacade);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            when(queryGateway.query(any())).thenReturn(
                    new MemoryLibraryQueryGateway.QueryResult(List.of(), 0L));

            MemoryLibrarySearchRequest request = new MemoryLibrarySearchRequest();
            request.setFamilyId(10L);
            request.setType("AI_SUMMARY");

            PageResult<MemoryLibraryItem> result = queryService.search(request);

            assertEquals(0L, result.getTotal());
            verify(familyService).checkMembership(10L);
        }
    }

    @Test
    void search_attachesTypedSocialStatsWithCompatibleJsonShape() throws Exception {
        MemoryLibraryQueryService queryService = new MemoryLibraryQueryService(
                queryGateway,
                familyService,
                memoryVoteFacade,
                growthStalenessFacade);
        MemoryLibraryItem memoryItem = MemoryLibraryItem.builder()
                .id("memory-88")
                .sourceType("FAMILY_EXPERIENCE")
                .metadata(java.util.Map.of())
                .build();
        MemoryLibraryItem growthItem = MemoryLibraryItem.builder()
                .id("growth-55")
                .sourceType("GROWTH_OBSERVATION")
                .metadata(java.util.Map.of())
                .build();
        when(queryGateway.query(any())).thenReturn(
                new MemoryLibraryQueryGateway.QueryResult(List.of(memoryItem, growthItem), 2L));
        when(memoryVoteFacade.getStats(88L, 101L))
                .thenReturn(new MemoryVoteStats(88L, 2, 1, 1, 1.3, null));
        when(growthStalenessFacade.getStats(55L, 101L))
                .thenReturn(new GrowthStalenessStats(55L, 3, 0.6, true));
        MemoryLibrarySearchRequest request = new MemoryLibrarySearchRequest();
        request.setFamilyId(10L);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            queryService.search(request);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        Object voteStats = memoryItem.getMetadata().get("voteStats");
        Object stalenessStats = growthItem.getMetadata().get("stalenessStats");
        assertTrue(voteStats instanceof MemoryVoteStats);
        assertTrue(stalenessStats instanceof GrowthStalenessStats);
        assertEquals(
                objectMapper.valueToTree(java.util.Map.of(
                        "memoryId", 88L,
                        "upVotes", 2,
                        "downVotes", 1,
                        "voteScore", 1,
                        "consensusWeight", 1.3,
                        "myVote", "")),
                objectMapper.valueToTree(voteStats));
        assertEquals(
                objectMapper.valueToTree(java.util.Map.of(
                        "recordId", 55L,
                        "staleVotes", 3,
                        "stalenessWeight", 0.6,
                        "myVoted", true)),
                objectMapper.valueToTree(stalenessStats));
    }

    @Test
    void search_requiresFamilyId() {
        MemoryLibraryQueryService queryService = new MemoryLibraryQueryService(
                queryGateway, familyService,
                memoryVoteFacade, growthStalenessFacade);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> queryService.search(new MemoryLibrarySearchRequest()));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
    }

    // --- MemoryLibraryMaintenanceService ---

    @Test
    void deleteArchivedLibraryItem_deletesArchivedMemoryAndEmbeddings() {
        MemoryLibraryMaintenanceService maintenanceService = maintenanceService();

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            MemoryEntry entry = new MemoryEntry();
            entry.setId(88L);
            entry.setFamilyId(10L);
            entry.setUserId(101L);
            entry.setStatus("ARCHIVED");
            when(memoryEntryRepository.findById(88L)).thenReturn(entry);

            maintenanceService.deleteArchivedItem(10L, "memory-88");

            verify(familyService).checkMembership(10L);
            verify(embeddingFacade).deleteMemoryIndex(88L);
            verify(memoryEntryRepository).delete(88L);
        }
    }

    @Test
    void deleteArchivedLibraryItem_rejectsActiveMemory() {
        MemoryLibraryMaintenanceService maintenanceService = maintenanceService();

        MemoryEntry entry = new MemoryEntry();
        entry.setId(88L);
        entry.setFamilyId(10L);
        entry.setUserId(101L);
        entry.setStatus("ACTIVE");
        when(memoryEntryRepository.findById(88L)).thenReturn(entry);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> maintenanceService.deleteArchivedItem(10L, "memory-88"));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(familyService).checkMembership(10L);
        verify(memoryEntryRepository, never()).delete(88L);
    }

    @Test
    void deleteArchivedLibraryItem_deletesActiveLegacyAiSummary() {
        MemoryLibraryMaintenanceService maintenanceService = maintenanceService();

        MemoryEntry entry = new MemoryEntry();
        entry.setId(89L);
        entry.setFamilyId(10L);
        entry.setUserId(101L);
        entry.setStatus("ACTIVE");
        entry.setMetadata(java.util.Map.of("source", MemoryLibrarySupport.LEGACY_AI_SUMMARY_SOURCE));
        when(memoryEntryRepository.findById(89L)).thenReturn(entry);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);

            maintenanceService.deleteArchivedItem(10L, "memory-89");

            verify(familyService).checkMembership(10L);
            verify(embeddingFacade).deleteMemoryIndex(89L);
            verify(memoryEntryRepository).delete(89L);
        }
    }

    @Test
    void updateLibraryItem_updatesActiveMemoryAndSchedulesReindex() {
        MemoryLibraryMaintenanceService maintenanceService = maintenanceService();

        MemoryEntry entry = new MemoryEntry();
        entry.setId(88L);
        entry.setFamilyId(10L);
        entry.setUserId(101L);
        entry.setStatus("ACTIVE");
        entry.setType("ELDER_ADVICE");
        entry.setScope("FAMILY_VISIBLE");
        entry.setImportance(3);
        entry.setContent("old content");
        when(memoryEntryRepository.findById(88L)).thenReturn(entry);

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
            assertEquals("Updated title", entry.getTitle());
            assertEquals("Updated title", entry.getSummary());
            assertEquals("INSIGHT", entry.getType());
            assertEquals("CARE_VISIBLE", entry.getScope());
            assertEquals(List.of("family", "shared"), ((java.util.Map<?, ?>) entry.getMetadata()).get("tags"));
            verify(memoryEntryRepository).update(entry);
            verify(memoryEmbeddingService).indexMemoryAfterCommit(entry);
        }
    }

    @Test
    void updateLibraryItem_rejectsArchivedMemory() {
        MemoryLibraryMaintenanceService maintenanceService = maintenanceService();

        MemoryEntry entry = new MemoryEntry();
        entry.setId(88L);
        entry.setFamilyId(10L);
        entry.setUserId(101L);
        entry.setStatus("ARCHIVED");
        when(memoryEntryRepository.findById(88L)).thenReturn(entry);

        MemoryLibraryUpdateRequest request = new MemoryLibraryUpdateRequest();
        request.setFamilyId(10L);
        request.setItemId("memory-88");
        request.setBody("Updated content");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> maintenanceService.updateItem(request));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(memoryEntryRepository, never()).update(entry);
        verify(memoryEmbeddingService, never()).indexMemoryAfterCommit(any());
    }

    @Test
    void updateLibraryItem_rejectsNonAuthorEvenWhenFamilyMember() {
        MemoryLibraryMaintenanceService maintenanceService = maintenanceService();

        MemoryEntry entry = new MemoryEntry();
        entry.setId(88L);
        entry.setFamilyId(10L);
        entry.setUserId(101L);
        entry.setStatus("ACTIVE");
        entry.setType("ELDER_ADVICE");
        entry.setScope("FAMILY_VISIBLE");
        when(memoryEntryRepository.findById(88L)).thenReturn(entry);

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
        verify(memoryEntryRepository, never()).update(entry);
        verify(memoryEmbeddingService, never()).indexMemoryAfterCommit(any());
    }

    private MemoryLibraryMaintenanceService maintenanceService() {
        MemoryLibraryMemoryCommandService memoryCommands = new MemoryLibraryMemoryCommandService(
                memoryEntryRepository,
                memoryEmbeddingService,
                embeddingFacade,
                metadataFacade);
        MemoryLibraryDiaryCommandService diaryCommands = new MemoryLibraryDiaryCommandService(
                diaryFacade,
                memoryEmbeddingService,
                embeddingFacade,
                metadataFacade);
        MemoryLibraryGrowthCommandService growthCommands = new MemoryLibraryGrowthCommandService(
                growthFacade,
                memoryEmbeddingService,
                embeddingFacade,
                metadataFacade);
        return new MemoryLibraryMaintenanceService(
                familyService,
                memoryCommands,
                diaryCommands,
                growthCommands);
    }

}
