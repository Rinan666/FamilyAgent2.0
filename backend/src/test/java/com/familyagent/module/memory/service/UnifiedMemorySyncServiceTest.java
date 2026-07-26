package com.familyagent.module.memory.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;
import com.familyagent.module.memory.gateway.UnifiedMemorySyncGateway;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnifiedMemorySyncServiceTest {

    private final UnifiedMemorySyncGateway gateway = mock(UnifiedMemorySyncGateway.class);
    private final UnifiedMemorySyncService service = new UnifiedMemorySyncService(gateway);

    @Test
    void sync_delegatesCompleteCanonicalRecordToGateway() {
        UnifiedMemorySyncRequest request = request("A family observation");
        when(gateway.upsert(request)).thenReturn(81L);

        Long result = service.sync(request);

        assertEquals(81L, result);
        verify(gateway).upsert(request);
    }

    @Test
    void sync_rejectsBlankContentBeforeDatabaseWrite() {
        UnifiedMemorySyncRequest request = request("  ");

        assertThrows(BusinessException.class, () -> service.sync(request));

        verify(gateway, never()).upsert(request);
    }

    private static UnifiedMemorySyncRequest request(String content) {
        return new UnifiedMemorySyncRequest(
                10L,
                1L,
                22L,
                MemoryContentType.OBSERVATION,
                MemoryScope.CARE_VISIBLE,
                "Observation",
                content,
                List.of("sleep"),
                LocalDateTime.of(2026, 7, 26, 10, 0),
                MemoryOriginType.GROWTH,
                77L,
                EntityStatus.ACTIVE);
    }
}
