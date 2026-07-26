package com.familyagent.module.memory.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.family.facade.FamilyRelationshipGraphFacade;
import com.familyagent.module.family.facade.FamilyRelationshipGraphView;
import com.familyagent.module.family.facade.FamilyRelationshipNode;
import com.familyagent.module.memory.dto.SharedPersonalMemoryView;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.repository.PersonalMemoryFamilyGrantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalMemoryQueryServiceTest {

    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private PersonalMemoryFamilyGrantRepository grantRepository;
    @Mock private FamilyMembershipFacade familyMembershipFacade;
    @Mock private FamilyRelationshipGraphFacade relationshipGraphFacade;

    @Test
    void listSharedWithFamily_mapsExplicitRelationshipWithoutLeakingOtherFamilyGrants() {
        MemoryEntry entry = personalMemory(77L, 202L);
        when(memoryRepository.findVisibleSharedPersonalMemories(10L, 101L, 100))
                .thenReturn(List.of(entry));
        when(relationshipGraphFacade.resolve(10L, 101L, null, Set.of(202L)))
                .thenReturn(new FamilyRelationshipGraphView(Map.of(
                        202L,
                        new FamilyRelationshipNode(
                                202L,
                                "Uncle Zhang",
                                "Second uncle",
                                null,
                                false,
                                false))));

        List<SharedPersonalMemoryView> result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            result = service().listSharedWithFamily(10L, 500);
        }

        verify(familyMembershipFacade).checkMembership(10L, 101L);
        assertEquals(1, result.size());
        assertEquals("Uncle Zhang", result.get(0).ownerName());
        assertEquals("Second uncle", result.get(0).relationshipToViewer());
        assertEquals(202L, result.get(0).ownerUserId());
        assertEquals("Shared note", result.get(0).content());
        verify(grantRepository, never()).findByMemoryIds(List.of(77L));
    }

    @Test
    void listSharedWithFamily_stopsBeforeQueryWhenViewerIsNotFamilyMember() {
        doThrow(new IllegalStateException("not a member"))
                .when(familyMembershipFacade).checkMembership(10L, 101L);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            assertThrows(IllegalStateException.class, () -> service().listSharedWithFamily(10L, 20));
        }

        verify(memoryRepository, never()).findVisibleSharedPersonalMemories(10L, 101L, 20);
        verify(relationshipGraphFacade, never()).resolve(10L, 101L, null, Set.of());
    }

    private PersonalMemoryQueryService service() {
        return new PersonalMemoryQueryService(
                memoryRepository,
                grantRepository,
                familyMembershipFacade,
                relationshipGraphFacade);
    }

    private static MemoryEntry personalMemory(Long id, Long userId) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setUserId(userId);
        entry.setLibraryKind("PERSONAL");
        entry.setType("NOTE");
        entry.setScope("SELECTED_FAMILIES_VISIBLE");
        entry.setContent("Shared note");
        entry.setSummary("Summary");
        entry.setImportance(3);
        entry.setStatus("ACTIVE");
        return entry;
    }
}
