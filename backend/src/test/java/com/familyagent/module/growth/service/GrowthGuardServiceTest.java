package com.familyagent.module.growth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.service.CareAuthorizationService;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.repository.GrowthGuardReportRepository;
import com.familyagent.module.growth.repository.GrowthGuardStalenessVoteRepository;
import com.familyagent.module.memory.service.MemoryEmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthGuardServiceTest {

    @Mock private GrowthGuardRecordRepository recordRepository;
    @Mock private GrowthGuardReportRepository reportRepository;
    @Mock private GrowthGuardStalenessVoteRepository stalenessVoteRepository;
    @Mock private FamilyService familyService;
    @Mock private FamilyMemberRepository memberRepository;
    @Mock private CareAuthorizationService careAuthorizationService;
    @Mock private MemoryEmbeddingService memoryEmbeddingService;

    @Test
    void searchFamilyRecords_shouldClampPageAndAttachStalenessStats() {
        GrowthGuardService service = new GrowthGuardService(
                recordRepository,
                reportRepository,
                stalenessVoteRepository,
                familyService,
                memberRepository,
                careAuthorizationService,
                memoryEmbeddingService);

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
    }
}
