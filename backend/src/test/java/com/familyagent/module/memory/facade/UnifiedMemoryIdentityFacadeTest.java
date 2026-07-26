package com.familyagent.module.memory.facade;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnifiedMemoryIdentityFacadeTest {

    @Test
    void resolvesPublicRecordIdToCanonicalMemoryId() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        when(repository.findIdByOrigin("DIARY", 51L)).thenReturn(151L);
        UnifiedMemoryIdentityFacade facade = new UnifiedMemoryIdentityFacade(repository);

        assertEquals(151L, facade.requireMemoryEntryId(MemoryOriginType.DIARY, 51L));
    }

    @Test
    void requireRejectsMissingCompatibilityRecord() {
        UnifiedMemoryIdentityFacade facade = new UnifiedMemoryIdentityFacade(mock(MemoryEntryRepository.class));

        assertThrows(BusinessException.class,
                () -> facade.requireMemoryEntryId(MemoryOriginType.GROWTH, 61L));
    }
}
