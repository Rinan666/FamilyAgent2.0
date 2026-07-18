package com.familyagent.module.memorylibrary.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.MemoryLibraryGrowthFacade;
import com.familyagent.module.memory.facade.MemoryIndexingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryEmbeddingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryIndexMetadataFacade;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryUpdateRequest;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryLibraryGrowthCommandServiceTest {

    @Test
    void update_shouldPreserveGrowthContractAndScheduleIndexing() {
        MemoryLibraryGrowthFacade growthFacade = mock(MemoryLibraryGrowthFacade.class);
        MemoryIndexingFacade indexingFacade = mock(MemoryIndexingFacade.class);
        MemoryLibraryEmbeddingFacade embeddingFacade = mock(MemoryLibraryEmbeddingFacade.class);
        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setId(55L);
        record.setFamilyId(10L);
        record.setCreatedBy(101L);
        record.setStatus("ACTIVE");
        record.setCategory("OTHER");
        record.setVisibility("FAMILY_VISIBLE");
        when(growthFacade.findById(55L)).thenReturn(record);
        MemoryLibraryUpdateRequest request = new MemoryLibraryUpdateRequest();
        request.setFamilyId(10L);
        request.setBody("Updated observation");
        request.setType("SLEEP");
        request.setVisibility("CARE_VISIBLE");
        request.setTags(List.of("follow-up"));
        MemoryLibraryGrowthCommandService service = new MemoryLibraryGrowthCommandService(
                growthFacade,
                indexingFacade,
                embeddingFacade,
                new MemoryLibraryIndexMetadataFacade());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            service.update(request, 55L);
        }

        assertEquals("Updated observation", record.getContent());
        assertEquals("SLEEP", record.getCategory());
        assertEquals("CARE_VISIBLE", record.getVisibility());
        assertEquals(List.of("follow-up"), ((Map<?, ?>) record.getMetadata()).get("tags"));
        verify(growthFacade).update(record);
        verify(indexingFacade).indexGrowthAfterCommit(record);
    }

    @Test
    void deleteArchived_shouldDeleteEmbeddingBeforeGrowthRecord() {
        MemoryLibraryGrowthFacade growthFacade = mock(MemoryLibraryGrowthFacade.class);
        MemoryIndexingFacade indexingFacade = mock(MemoryIndexingFacade.class);
        MemoryLibraryEmbeddingFacade embeddingFacade = mock(MemoryLibraryEmbeddingFacade.class);
        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setId(55L);
        record.setFamilyId(10L);
        record.setCreatedBy(101L);
        record.setStatus("ARCHIVED");
        when(growthFacade.findById(55L)).thenReturn(record);
        MemoryLibraryGrowthCommandService service = new MemoryLibraryGrowthCommandService(
                growthFacade,
                indexingFacade,
                embeddingFacade,
                new MemoryLibraryIndexMetadataFacade());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            service.deleteArchived(10L, 55L);
        }

        InOrder deletionOrder = inOrder(embeddingFacade, growthFacade);
        deletionOrder.verify(embeddingFacade).deleteGrowthIndex(55L);
        deletionOrder.verify(growthFacade).delete(55L);
    }
}
