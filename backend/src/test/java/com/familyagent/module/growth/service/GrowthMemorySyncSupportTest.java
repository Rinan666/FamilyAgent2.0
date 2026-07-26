package com.familyagent.module.growth.service;

import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.facade.UnifiedMemorySyncFacade;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
    }
}
