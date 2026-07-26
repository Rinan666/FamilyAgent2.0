package com.familyagent.module.memory.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.family.facade.FamilyMembershipQueryFacade;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.facade.UnifiedMemoryCreateResult;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;
import com.familyagent.module.memory.gateway.UnifiedMemorySyncGateway;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnifiedMemorySyncServiceTest {

    private final UnifiedMemorySyncGateway gateway = mock(UnifiedMemorySyncGateway.class);
    private final FamilyMembershipQueryFacade membershipQueryFacade = mock(FamilyMembershipQueryFacade.class);
    private final MemoryEntryRepository memoryRepository = mock(MemoryEntryRepository.class);
    private final MemoryEmbeddingService embeddingService = mock(MemoryEmbeddingService.class);
    private final UnifiedMemorySyncService service = new UnifiedMemorySyncService(
            gateway,
            membershipQueryFacade,
            memoryRepository,
            embeddingService);

    @Test
    void syncIndexesTheCanonicalMemoryEntry() {
        UnifiedMemorySyncRequest request = request("A family observation", EntityStatus.ACTIVE);
        MemoryEntry entry = entry(81L, EntityStatus.ACTIVE);
        when(membershipQueryFacade.isMember(1L, 22L)).thenReturn(true);
        when(gateway.upsert(request)).thenReturn(81L);
        when(memoryRepository.selectById(81L)).thenReturn(entry);

        assertEquals(81L, service.sync(request));

        verify(gateway).upsert(request);
        verify(embeddingService).indexMemoryAfterCommit(entry);
    }

    @Test
    void createAllocatesPublicOriginIdAndIndexesCanonicalEntry() {
        UnifiedMemorySyncRequest request = createRequest("A new observation");
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 27, 10, 0);
        UnifiedMemoryCreateResult created = new UnifiedMemoryCreateResult(181L, 91L, createdAt, createdAt);
        MemoryEntry entry = entry(181L, EntityStatus.ACTIVE);
        when(membershipQueryFacade.isMember(1L, 22L)).thenReturn(true);
        when(gateway.insert(request)).thenReturn(created);
        when(memoryRepository.selectById(181L)).thenReturn(entry);

        assertEquals(created, service.create(request));

        verify(gateway).insert(request);
        verify(embeddingService).indexMemoryAfterCommit(entry);
    }

    @Test
    void syncArchivedRecordDeletesCanonicalIndex() {
        UnifiedMemorySyncRequest request = request("Archived observation", EntityStatus.ARCHIVED);
        when(membershipQueryFacade.isMember(1L, 22L)).thenReturn(true);
        when(gateway.upsert(request)).thenReturn(82L);
        when(memoryRepository.selectById(82L)).thenReturn(entry(82L, EntityStatus.ARCHIVED));

        service.sync(request);

        verify(embeddingService).deleteMemoryIndexAfterCommit(82L);
    }

    @Test
    void syncClearsRelatedUserOutsideTheFamily() {
        UnifiedMemorySyncRequest request = request("A family observation", EntityStatus.ACTIVE);
        when(membershipQueryFacade.isMember(1L, 22L)).thenReturn(false);
        when(gateway.upsert(org.mockito.ArgumentMatchers.any())).thenReturn(83L);
        when(memoryRepository.selectById(83L)).thenReturn(entry(83L, EntityStatus.ACTIVE));

        service.sync(request);

        ArgumentCaptor<UnifiedMemorySyncRequest> captor = ArgumentCaptor.forClass(UnifiedMemorySyncRequest.class);
        verify(gateway).upsert(captor.capture());
        assertNull(captor.getValue().relatedUserId());
    }

    @Test
    void deleteRemovesCanonicalIndexByReturnedMemoryId() {
        when(gateway.delete(MemoryOriginType.DIARY, 77L)).thenReturn(177L);

        service.delete(MemoryOriginType.DIARY, 77L);

        verify(embeddingService).deleteMemoryIndexAfterCommit(177L);
    }

    @Test
    void syncRejectsBlankContentBeforeDatabaseWrite() {
        UnifiedMemorySyncRequest request = request("  ", EntityStatus.ACTIVE);

        assertThrows(BusinessException.class, () -> service.sync(request));
        verify(gateway, never()).upsert(request);
    }

    private static UnifiedMemorySyncRequest request(String content, EntityStatus status) {
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
                status);
    }

    private static UnifiedMemorySyncRequest createRequest(String content) {
        return new UnifiedMemorySyncRequest(
                10L,
                1L,
                22L,
                MemoryContentType.OBSERVATION,
                MemoryScope.CARE_VISIBLE,
                "Observation",
                content,
                List.of("sleep"),
                LocalDateTime.of(2026, 7, 27, 10, 0),
                MemoryOriginType.GROWTH,
                null,
                EntityStatus.ACTIVE);
    }

    private static MemoryEntry entry(Long id, EntityStatus status) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setStatus(status.name());
        return entry;
    }
}
