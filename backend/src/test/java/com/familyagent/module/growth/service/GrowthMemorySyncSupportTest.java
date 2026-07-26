package com.familyagent.module.growth.service;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.facade.UnifiedMemoryCreateResult;
import com.familyagent.module.memory.facade.UnifiedMemorySyncFacade;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrowthMemorySyncSupportTest {

    private final UnifiedMemorySyncFacade syncFacade = mock(UnifiedMemorySyncFacade.class);
    private final GrowthMemorySyncSupport support = new GrowthMemorySyncSupport(syncFacade);

    @Test
    void sync_keepsOnlySimpleObservationFieldsInCanonicalContract() {
        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setId(61L);
        record.setCreatedBy(10L);
        record.setFamilyId(1L);
        record.setTargetUserId(22L);
        record.setContent("Recently rubs eyes while reading");
        record.setCategory("VISION");
        record.setSeverity(4);
        record.setVisibility("CARE_VISIBLE");
        record.setObservedAt(LocalDate.of(2026, 7, 26));
        record.setStatus("ACTIVE");
        record.setMetadata(Map.of("title", "Reading observation", "tags", List.of("vision")));

        support.sync(record);

        ArgumentCaptor<UnifiedMemorySyncRequest> captor = ArgumentCaptor.forClass(UnifiedMemorySyncRequest.class);
        verify(syncFacade).sync(captor.capture());
        assertEquals("OBSERVATION", captor.getValue().type().name());
        assertEquals("GROWTH", captor.getValue().originType().name());
        assertEquals(List.of("vision"), captor.getValue().tags());
        assertEquals("VISION", captor.getValue().metadata().legacyGrowth().category());
        assertEquals(4, captor.getValue().metadata().legacyGrowth().severity());
        assertEquals("Reading observation", captor.getValue().metadata().extra().get("title"));
    }

    @Test
    void createKeepsInternalAndPublicIdsSeparate() {
        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setCreatedBy(10L);
        record.setFamilyId(1L);
        record.setTargetUserId(22L);
        record.setContent("Keep observing");
        record.setCategory("SLEEP");
        record.setSeverity(3);
        record.setVisibility("CARE_VISIBLE");
        record.setObservedAt(LocalDate.of(2026, 7, 27));
        record.setStatus("ACTIVE");
        record.setMetadata(Map.of());
        LocalDateTime timestamp = LocalDateTime.of(2026, 7, 27, 12, 0);
        when(syncFacade.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UnifiedMemoryCreateResult(161L, 61L, timestamp, timestamp));

        support.create(record);

        assertEquals(61L, record.getId());
        assertEquals(161L, record.getMemoryEntryId());
        verify(syncFacade).create(org.mockito.ArgumentMatchers.any());
    }
}
