package com.familyagent.module.family.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.lifecycle.FamilyScopedResourceCleaner;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.entity.Family;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.repository.FamilyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyLifecycleServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private FamilyRepository familyRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private FamilyScopedResourceCleaner resourceCleaner;
    private FamilyLifecycleService familyLifecycleService;

    @BeforeEach
    void setUp() {
        familyLifecycleService = new FamilyLifecycleService(
                jdbcTemplate,
                familyRepository,
                familyMemberRepository,
                List.of(resourceCleaner));
    }

    @Test
    void prepareFamiliesForUserDeletion_blocksOwnerWithRemainingMembers() {
        when(familyMemberRepository.findByUserId(8L)).thenReturn(List.of(member(10L, 8L, "OWNER")));
        when(familyMemberRepository.findByFamilyId(10L)).thenReturn(List.of(
                member(10L, 8L, "OWNER"),
                member(10L, 9L, "MEMBER")));
        when(familyRepository.selectById(10L)).thenReturn(family(10L, "Smith Family"));

        BusinessException error = assertThrows(BusinessException.class, () -> familyLifecycleService.prepareFamiliesForUserDeletion(8L));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), error.getCode());
        verify(familyMemberRepository, never()).removeByFamilyId(10L);
        verify(familyRepository, never()).deleteById(10L);
    }

    @Test
    void prepareFamiliesForUserDeletion_dissolvesFamilyForLastRemainingOwner() {
        when(familyMemberRepository.findByUserId(8L)).thenReturn(List.of(member(10L, 8L, "OWNER")));
        when(familyMemberRepository.findByFamilyId(10L)).thenReturn(List.of(member(10L, 8L, "OWNER")));
        when(familyRepository.selectById(10L)).thenReturn(family(10L, "Solo Family"));
        when(jdbcTemplate.queryForObject(eq("SELECT to_regclass(?) IS NOT NULL"), eq(Boolean.class), anyString()))
                .thenReturn(true);

        familyLifecycleService.prepareFamiliesForUserDeletion(8L);

        verify(familyMemberRepository).removeByFamilyId(10L);
        verify(jdbcTemplate).update("DELETE FROM family_relationships WHERE family_id = ?", 10L);
        verify(familyRepository).deleteById(10L);
    }

    @Test
    void transferOwner_updatesRolesAndCreatedBy() {
        when(familyRepository.selectById(10L)).thenReturn(family(10L, "Smith Family"));
        when(familyMemberRepository.findByFamilyAndUser(10L, 9L)).thenReturn(member(10L, 9L, "MEMBER"));
        when(familyMemberRepository.findByFamilyId(10L)).thenReturn(List.of(
                member(10L, 8L, "OWNER"),
                member(10L, 9L, "MEMBER")));

        familyLifecycleService.transferOwner(10L, 9L, 1L);

        verify(familyMemberRepository).updateRole(10L, 8L, "MEMBER");
        verify(familyMemberRepository).updateRole(10L, 9L, "OWNER");
        verify(familyRepository).updateCreatedBy(10L, 9L);
    }

    @Test
    void transferOwner_rejectsNonMember() {
        when(familyRepository.selectById(10L)).thenReturn(family(10L, "Smith Family"));
        when(familyMemberRepository.findByFamilyAndUser(10L, 9L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> familyLifecycleService.transferOwner(10L, 9L, 1L));

        assertEquals(ErrorCode.NOT_FAMILY_MEMBER.getCode(), error.getCode());
        verify(familyRepository, never()).updateCreatedBy(10L, 9L);
    }

    @Test
    void auditHistoricalFamilyStates_reportsSuspiciousFamiliesWithoutDeletingThem() {
        when(jdbcTemplate.queryForObject(eq("SELECT to_regclass(?) IS NOT NULL"), eq(Boolean.class), anyString()))
                .thenReturn(true);
        when(jdbcTemplate.query(eq("""
                SELECT
                    f.id AS family_id,
                    f.name AS family_name,
                    COUNT(fm.id) AS member_count,
                    COUNT(*) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') AS owner_count
                FROM families f
                LEFT JOIN family_members fm ON fm.family_id = f.id
                GROUP BY f.id, f.name
                ORDER BY f.id ASC
                """), org.mockito.ArgumentMatchers.<RowMapper<FamilyLifecycleService.FamilyOwnerState>>any()))
                .thenReturn(List.of(
                        new FamilyLifecycleService.FamilyOwnerState(10L, "Empty", 0, 0),
                        new FamilyLifecycleService.FamilyOwnerState(11L, "Ownerless", 2, 0),
                        new FamilyLifecycleService.FamilyOwnerState(12L, "Split", 3, 2),
                        new FamilyLifecycleService.FamilyOwnerState(13L, "Healthy", 2, 1)));

        List<FamilyLifecycleService.FamilyOwnerState> result = familyLifecycleService.auditHistoricalFamilyStates();

        assertEquals(4, result.size());
        assertEquals(0, result.get(0).memberCount());
        assertEquals(0, result.get(1).ownerCount());
        assertEquals(2, result.get(2).ownerCount());
        verify(familyMemberRepository, never()).removeByFamilyId(10L);
        verify(familyMemberRepository, never()).removeByFamilyId(11L);
        verify(familyRepository, never()).deleteById(10L);
        verify(familyRepository, never()).deleteById(11L);
        verify(familyRepository, never()).deleteById(12L);
    }

    @Test
    void dissolveFamily_deletesAllFamilyScopedTablesInOrder() {
        when(familyRepository.selectById(10L)).thenReturn(family(10L, "Test Family"));
        when(jdbcTemplate.queryForObject(eq("SELECT to_regclass(?) IS NOT NULL"), eq(Boolean.class), anyString()))
                .thenReturn(true);

        familyLifecycleService.dissolveFamily(10L, "TEST");

        InOrder inOrder = inOrder(resourceCleaner, familyMemberRepository);
        inOrder.verify(resourceCleaner).cleanFamilyResources(10L);
        inOrder.verify(familyMemberRepository).removeByFamilyId(10L);
        verify(jdbcTemplate).update("DELETE FROM family_relationships WHERE family_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM care_authorizations WHERE family_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM growth_guard_staleness_votes WHERE family_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM memory_entry_votes WHERE family_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM photos WHERE family_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM family_persona_members WHERE family_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM growth_guard_reports WHERE family_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM memory_embeddings WHERE family_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM heritage_tasks WHERE family_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM skill_runs WHERE family_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM memory_entries WHERE family_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM chat_sessions WHERE family_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM mirror_agent_data WHERE primary_family_id = ?", 10L);
        verify(familyRepository).deleteById(10L);
    }

    @Test
    void dissolveFamily_isNoOpWhenFamilyNotFound() {
        when(familyRepository.selectById(99L)).thenReturn(null);

        familyLifecycleService.dissolveFamily(99L, "TEST");

        verify(familyMemberRepository, never()).removeByFamilyId(99L);
        verify(familyRepository, never()).deleteById(99L);
    }

    @Test
    void dissolveFamily_skipsTableDeleteWhenTableDoesNotExist() {
        when(familyRepository.selectById(10L)).thenReturn(family(10L, "Test Family"));
        when(jdbcTemplate.queryForObject(eq("SELECT to_regclass(?) IS NOT NULL"), eq(Boolean.class), anyString()))
                .thenReturn(false);

        familyLifecycleService.dissolveFamily(10L, "TEST");

        verify(familyMemberRepository).removeByFamilyId(10L);
        verify(jdbcTemplate, never()).update(anyString(), eq(10L));
        verify(familyRepository).deleteById(10L);
    }

    private static Family family(Long id, String name) {
        Family family = new Family();
        family.setId(id);
        family.setName(name);
        return family;
    }

    private static FamilyMember member(Long familyId, Long userId, String role) {
        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }
}
