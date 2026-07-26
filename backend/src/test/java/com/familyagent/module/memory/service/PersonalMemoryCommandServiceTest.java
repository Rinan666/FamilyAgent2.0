package com.familyagent.module.memory.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.module.memory.dto.CreatePersonalMemoryRequest;
import com.familyagent.module.memory.dto.PersonalMemoryView;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.repository.PersonalMemoryFamilyGrantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalMemoryCommandServiceTest {

    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private PersonalMemoryFamilyGrantRepository grantRepository;
    @Mock private PersonalMemoryVisibilityPolicy visibilityPolicy;
    @Mock private MemoryEmbeddingService embeddingService;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;

    @Test
    void create_persistsPersonalOwnershipAndExplicitFamilyGrants() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(visibilityPolicy.resolve(101L, "SELECTED_FAMILIES_VISIBLE", List.of(10L, 20L)))
                .thenReturn(new PersonalMemoryVisibilityPolicy.VisibilityGrant(
                        "SELECTED_FAMILIES_VISIBLE",
                        List.of(10L, 20L)));
        doAnswer(invocation -> {
            MemoryEntry entry = invocation.getArgument(0);
            entry.setId(77L);
            return 1;
        }).when(memoryRepository).insert(any(MemoryEntry.class));
        CreatePersonalMemoryRequest request = new CreatePersonalMemoryRequest();
        request.setContent("A new idea worth remembering");
        request.setSummary("New idea");
        request.setType("KNOWLEDGE");
        request.setVisibility("SELECTED_FAMILIES_VISIBLE");
        request.setSelectedFamilyIds(List.of(10L, 20L));

        PersonalMemoryView result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            result = service().create(request);
        }

        ArgumentCaptor<MemoryEntry> captor = ArgumentCaptor.forClass(MemoryEntry.class);
        verify(memoryRepository).insert(captor.capture());
        assertEquals("PERSONAL", captor.getValue().getLibraryKind());
        assertEquals(null, captor.getValue().getFamilyId());
        assertEquals("SELECTED_FAMILIES_VISIBLE", captor.getValue().getScope());
        verify(grantRepository).insertGrant(77L, 10L, 101L);
        verify(grantRepository).insertGrant(77L, 20L, 101L);
        verify(embeddingService).indexMemoryAfterCommit(captor.getValue());
        assertEquals(List.of(10L, 20L), result.selectedFamilyIds());
    }

    private PersonalMemoryCommandService service() {
        return new PersonalMemoryCommandService(
                memoryRepository,
                grantRepository,
                visibilityPolicy,
                embeddingService,
                redissonClient);
    }
}
