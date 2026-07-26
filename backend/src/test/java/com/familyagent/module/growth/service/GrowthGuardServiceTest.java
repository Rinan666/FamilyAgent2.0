package com.familyagent.module.growth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.module.growth.dto.CreateGrowthGuardRecordRequest;
import com.familyagent.module.growth.dto.GrowthGuardMetadata;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.repository.GrowthGuardStalenessVoteRepository;
import com.familyagent.module.memory.facade.MemoryIndexingFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthGuardServiceTest {

    @Mock private GrowthGuardRecordRepository recordRepository;
    @Mock private GrowthGuardStalenessVoteRepository stalenessVoteRepository;
    @Mock private PermissionGate permissionGate;
    @Mock private MemoryIndexingFacade memoryEmbeddingService;
    @Mock private GrowthMemorySyncSupport memorySyncSupport;

    @Test
    void createRecord_shouldPersistTypedMetadataAndDefaultFollowUpStatus() {
        GrowthGuardService service = service();
        CreateGrowthGuardRecordRequest request = new CreateGrowthGuardRecordRequest();
        request.setFamilyId(1L);
        request.setTargetUserId(22L);
        request.setCategory("SLEEP");
        request.setContent("最近入睡偏晚，需要继续观察。");
        request.setMetadata(GrowthGuardMetadata.fromMap(Map.of(
                "source", "MIRROR_AGENT_TOOL",
                "plannedTool", "GROWTH_GUARD")));

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            service.createRecord(request);
        }

        ArgumentCaptor<GrowthGuardRecord> captor = ArgumentCaptor.forClass(GrowthGuardRecord.class);
        verify(recordRepository).insert(captor.capture());
        Map<?, ?> metadata = (Map<?, ?>) captor.getValue().getMetadata();
        assertEquals("MIRROR_AGENT_TOOL", metadata.get("source"));
        assertEquals("GROWTH_GUARD", metadata.get("plannedTool"));
        assertEquals("PENDING", metadata.get("followUpStatus"));
        verify(memorySyncSupport).sync(captor.getValue());
    }

    @Test
    void searchFamilyRecords_shouldClampPageAndAttachStalenessStats() {
        GrowthGuardService service = service();

        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setId(401L);
        record.setFamilyId(1L);
        record.setTargetUserId(22L);
        record.setCreatedBy(10L);
        record.setCategory("SLEEP");
        record.setContent("最近入睡偏晚");
        record.setObservedAt(LocalDate.of(2026, 6, 8));
        record.setStatus("ACTIVE");
        record.setMetadata(Map.of());

        when(recordRepository.countVisibleByFamilySearch(1L, 10L, 22L, "入睡")).thenReturn(8L);
        when(recordRepository.searchVisibleByFamily(1L, 10L, 22L, "入睡", 6, 6L)).thenReturn(List.of(record));
        when(stalenessVoteRepository.statsByRecordId(401L, 10L)).thenReturn(null);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);

            var result = service.searchFamilyRecords(1L, 22L, " 入睡 ", 5, 0);

            assertEquals(2L, result.getPage());
            assertEquals(6L, result.getPageSize());
            assertEquals(8L, result.getTotal());
            assertEquals(1, result.getItems().size());
            assertTrue(((Map<?, ?>) result.getItems().get(0).getMetadata()).containsKey("stalenessStats"));
        }

        verify(permissionGate).checkMembership(1L);
    }

    @Test
    void markRecordStale_shouldTreatDuplicateInsertAsExistingVote() {
        GrowthGuardService service = service();

        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setId(401L);
        record.setFamilyId(1L);
        record.setTargetUserId(22L);
        record.setCreatedBy(10L);
        record.setCategory("SLEEP");
        record.setContent("鏈€杩戝叆鐫″亸鏅?");
        record.setObservedAt(LocalDate.of(2026, 6, 8));
        record.setStatus("ACTIVE");
        record.setMetadata(Map.of());

        when(recordRepository.selectById(401L)).thenReturn(record);
        when(stalenessVoteRepository.selectOne(any())).thenReturn(null, new com.familyagent.module.growth.entity.GrowthGuardStalenessVote());
        when(stalenessVoteRepository.statsByRecordId(401L, 10L)).thenReturn(null);
        when(stalenessVoteRepository.insert(any())).thenThrow(new DuplicateKeyException("duplicate stale vote"));

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            GrowthGuardRecord result = service.markRecordStale(401L);
            assertTrue(((Map<?, ?>) result.getMetadata()).containsKey("stalenessStats"));
        }

        verify(permissionGate).checkMembership(1L);
        verify(permissionGate).ensureCanViewRecord(record, 10L);
        verify(stalenessVoteRepository).insert(any());
    }
    private GrowthGuardService service() {
        return new GrowthGuardService(
                recordRepository,
                stalenessVoteRepository,
                permissionGate,
                memoryEmbeddingService,
                memorySyncSupport);
    }
}
