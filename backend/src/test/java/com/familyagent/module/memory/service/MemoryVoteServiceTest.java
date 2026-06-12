package com.familyagent.module.memory.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.entity.MemoryEntryVote;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryVoteServiceTest {

    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private MemoryEntryVoteRepository voteRepository;
    @Mock private FamilyService familyService;

    @Test
    void vote_shouldTreatDuplicateInsertAsExistingVote() {
        MemoryVoteService service = new MemoryVoteService(memoryRepository, voteRepository, familyService);

        MemoryEntry entry = new MemoryEntry();
        entry.setId(301L);
        entry.setFamilyId(1L);
        entry.setUserId(22L);
        entry.setType("ELDER_ADVICE");
        entry.setStatus("ACTIVE");
        entry.setMetadata(Map.of());

        MemoryEntryVote existingVote = new MemoryEntryVote();
        existingVote.setMemoryId(301L);
        existingVote.setUserId(10L);
        existingVote.setVoteType("UP");

        when(memoryRepository.selectById(301L)).thenReturn(entry);
        when(memoryRepository.findVisibleFamilyMemoryById(1L, 301L, 10L)).thenReturn(entry);
        when(voteRepository.selectOne(any())).thenReturn(null, existingVote);
        when(voteRepository.statsByMemoryId(301L, 10L)).thenReturn(null);
        when(voteRepository.insert(any())).thenThrow(new DuplicateKeyException("duplicate vote"));

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            MemoryEntry result = service.vote(301L, "UP");
            assertTrue(((Map<?, ?>) result.getMetadata()).containsKey("voteStats"));
        }

        verify(familyService).checkMembership(1L);
        verify(voteRepository).insert(any());
        verify(voteRepository, never()).updateById(any());
    }
}
