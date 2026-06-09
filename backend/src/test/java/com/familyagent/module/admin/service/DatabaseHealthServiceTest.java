package com.familyagent.module.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.admin.dto.AdminUserSummary;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticRequest;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticResponse;
import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.entity.Family;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.repository.FamilyRepository;
import com.familyagent.module.family.service.FamilyLifecycleService;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.dto.RecallSourceSummary;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class DatabaseHealthServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private UserRepository userRepository;
    @Mock private FamilyRepository familyRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private FamilyLifecycleService familyLifecycleService;
    @Mock private AuthorizedMemoryRecallService memoryRecallService;
    @InjectMocks private DatabaseHealthService databaseHealthService;

    @Test
    void deleteUser_requiresPlatformAdmin() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            User user = new User();
            user.setId(2L);
            user.setRole("USER");
            when(userRepository.findBasicById(2L)).thenReturn(user);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> databaseHealthService.deleteUser(101L));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
            verify(jdbcTemplate, never()).update("DELETE FROM users WHERE id = ?", 101L);
        }
    }

    @Test
    void deleteUser_rejectsDeletingCurrentAdmin() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(userRepository.findBasicById(1L)).thenReturn(adminUser());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> databaseHealthService.deleteUser(1L));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
            verify(jdbcTemplate, never()).update("DELETE FROM users WHERE id = ?", 1L);
        }
    }

    @Test
    void deleteUser_rejectsDeletingAnotherAdmin() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(userRepository.findBasicById(1L)).thenReturn(adminUser());

            User target = new User();
            target.setId(2L);
            target.setRole("ADMIN");
            when(userRepository.findBasicById(2L)).thenReturn(target);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> databaseHealthService.deleteUser(2L));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
            verify(familyLifecycleService, never()).prepareFamiliesForUserDeletion(2L);
            verify(jdbcTemplate, never()).update("DELETE FROM users WHERE id = ?", 2L);
        }
    }

    @Test
    void deleteUser_deletesUserAndRelatedRecords() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(userRepository.findBasicById(1L)).thenReturn(adminUser());

            User target = new User();
            target.setId(88L);
            target.setRole("USER");
            when(userRepository.findBasicById(88L)).thenReturn(target);

            when(jdbcTemplate.queryForObject(eq("SELECT to_regclass(?) IS NOT NULL"), eq(Boolean.class), anyString()))
                    .thenReturn(true);
            doReturn(1).when(jdbcTemplate).update(anyString(), eq(88L));

            databaseHealthService.deleteUser(88L);

            verify(familyLifecycleService).prepareFamiliesForUserDeletion(88L);
            verify(jdbcTemplate).update("UPDATE families SET created_by = NULL WHERE created_by = ?", 88L);
            verify(jdbcTemplate).update("DELETE FROM family_relationships WHERE from_user_id = ?", 88L);
            verify(jdbcTemplate).update("DELETE FROM memory_entry_votes WHERE user_id = ?", 88L);
            verify(jdbcTemplate).update("DELETE FROM chat_sessions WHERE user_id = ?", 88L);
            verify(jdbcTemplate).update("DELETE FROM family_members WHERE user_id = ?", 88L);
            verify(jdbcTemplate).update("DELETE FROM users WHERE id = ?", 88L);
        }
    }

    @Test
    void deleteUser_stopsWhenFamilyLifecycleBlocksDeletion() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(userRepository.findBasicById(1L)).thenReturn(adminUser());

            User target = new User();
            target.setId(88L);
            target.setRole("USER");
            when(userRepository.findBasicById(88L)).thenReturn(target);
            org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.BAD_REQUEST, "Transfer family owner first"))
                    .when(familyLifecycleService).prepareFamiliesForUserDeletion(88L);

            BusinessException error = assertThrows(BusinessException.class, () -> databaseHealthService.deleteUser(88L));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), error.getCode());
            verify(jdbcTemplate, never()).update("DELETE FROM users WHERE id = ?", 88L);
        }
    }

    @Test
    void diagnoseMemoryRecall_rejectsViewerOutsideFamily() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(userRepository.findBasicById(1L)).thenReturn(adminUser());
            when(familyMemberRepository.findByFamilyAndUser(10L, 999L)).thenReturn(null);

            MemoryRecallDiagnosticRequest request = new MemoryRecallDiagnosticRequest();
            request.setFamilyId(10L);
            request.setViewerUserId(999L);
            request.setQuery("tooth");

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> databaseHealthService.diagnoseMemoryRecall(request));

            assertEquals(ErrorCode.NOT_FAMILY_MEMBER.getCode(), exception.getCode());
            verify(memoryRecallService, never()).recallForFamilyAfterViewerValidated(
                    eq(10L),
                    eq(999L),
                    eq("tooth"),
                    anyInt(),
                    anyInt());
        }
    }

    @Test
    void diagnoseMemoryRecall_returnsOnlySafeSummaryFields() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(userRepository.findBasicById(1L)).thenReturn(adminUser());
            when(familyMemberRepository.findByFamilyAndUser(10L, 101L)).thenReturn(member(10L, 101L));
            when(memoryRecallService.recallForFamilyAfterViewerValidated(eq(10L), eq(101L), eq("brush teeth"), eq(2), eq(4)))
                    .thenReturn(AuthorizedMemoryRecallResult.builder()
                            .diaryCount(1)
                            .memoryCount(1)
                            .growthRecordCount(0)
                            .query("brush teeth")
                            .retrievalMode("TEXT_FALLBACK")
                            .embeddingReadyCount(7)
                            .sources(List.of(RecallSourceSummary.builder()
                                    .id("memory-2")
                                    .sourceType("FAMILY_EXPERIENCE")
                                    .title("Family advice")
                                    .snippet("Brush teeth before sleep")
                                    .visibility("FAMILY_VISIBLE")
                                    .build()))
                            .build());

            MemoryRecallDiagnosticRequest request = new MemoryRecallDiagnosticRequest();
            request.setFamilyId(10L);
            request.setViewerUserId(101L);
            request.setQuery("brush teeth");
            request.setDiaryLimit(2);
            request.setMemoryLimit(4);

            MemoryRecallDiagnosticResponse response = databaseHealthService.diagnoseMemoryRecall(request);

            assertEquals(10L, response.getFamilyId());
            assertEquals(101L, response.getViewerUserId());
            assertEquals("TEXT_FALLBACK", response.getRetrievalMode());
            assertEquals(7, response.getEmbeddingReadyCount());
            assertEquals(1, response.getDiaryCount());
            assertEquals(1, response.getMemoryCount());
            assertEquals(1, response.getSources().size());
            assertEquals("Brush teeth before sleep", response.getSources().get(0).getSnippet());
        }
    }

    @Test
    void diagnoseMemoryRecall_requiresPlatformAdmin() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            User user = new User();
            user.setId(2L);
            user.setRole("USER");
            when(userRepository.findBasicById(2L)).thenReturn(user);

            MemoryRecallDiagnosticRequest request = new MemoryRecallDiagnosticRequest();
            request.setFamilyId(10L);
            request.setViewerUserId(101L);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> databaseHealthService.diagnoseMemoryRecall(request));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
            verify(familyMemberRepository, never()).findByFamilyAndUser(10L, 101L);
        }
    }

    @Test
    void listUsers_requiresPlatformAdmin() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            User user = new User();
            user.setId(2L);
            user.setRole("USER");
            when(userRepository.findBasicById(2L)).thenReturn(user);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> databaseHealthService.listUsers());

            assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
            verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class));
        }
    }

    @Test
    void listUsers_returnsIdUsernameMapping() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(userRepository.findBasicById(1L)).thenReturn(adminUser());
            when(jdbcTemplate.queryForObject(eq("SELECT to_regclass(?) IS NOT NULL"), eq(Boolean.class), anyString()))
                    .thenReturn(true);
            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                    .thenReturn(List.of(
                            AdminUserSummary.builder().id(1L).username("admin").nickname("Admin").role("ADMIN").status("ACTIVE").build(),
                            AdminUserSummary.builder().id(2L).username("alice").nickname("Alice").role("USER").status("ACTIVE").build()
                    ));

            List<AdminUserSummary> users = databaseHealthService.listUsers();

            assertEquals(2, users.size());
            assertEquals(1L, users.get(0).getId());
            assertEquals("admin", users.get(0).getUsername());
            assertEquals(2L, users.get(1).getId());
            assertEquals("alice", users.get(1).getUsername());
        }
    }

    @Test
    void searchUsers_returnsPaginatedMatches() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(userRepository.findBasicById(1L)).thenReturn(adminUser());
            when(jdbcTemplate.queryForObject(eq("SELECT to_regclass(?) IS NOT NULL"), eq(Boolean.class), anyString()))
                    .thenReturn(true);
            when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Long.class), any(Object[].class)))
                    .thenReturn(1L);
            when(jdbcTemplate.query(contains("SELECT id, username, nickname, role, status"), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of(
                            AdminUserSummary.builder().id(2L).username("alice").nickname("Alice").role("USER").status("ACTIVE").build()
                    ));

            var page = databaseHealthService.searchUsers("alice", 1, 10);

            assertEquals(1L, page.getTotal());
            assertEquals(1, page.getItems().size());
            assertEquals("alice", page.getItems().get(0).getUsername());
        }
    }

    @Test
    void searchFamilies_returnsPaginatedMatches() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(userRepository.findBasicById(1L)).thenReturn(adminUser());
            when(jdbcTemplate.queryForObject(eq("SELECT to_regclass(?) IS NOT NULL"), eq(Boolean.class), anyString()))
                    .thenReturn(true);
            when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*)"), eq(Long.class), any(Object[].class)))
                    .thenReturn(1L);
            when(jdbcTemplate.query(contains("f.id AS family_id"), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of(
                            com.familyagent.module.admin.dto.FamilyDatabaseSummary.builder()
                                    .familyId(10L)
                                    .familyName("Smith Family")
                                    .memberCount(3L)
                                    .diaryCount(4L)
                                    .memoryCount(5L)
                                    .growthRecordCount(1L)
                                    .skillRunCount(2L)
                                    .failedSkillRunCount(0L)
                                    .readyEmbeddingCount(6L)
                                    .failedEmbeddingCount(0L)
                                    .build()
                    ));

            var page = databaseHealthService.searchFamilies("smith", 1, 10);

            assertEquals(1L, page.getTotal());
            assertEquals(1, page.getItems().size());
            assertEquals("Smith Family", page.getItems().get(0).getFamilyName());
        }
    }

    @Test
    void listFamilyMembers_requiresPlatformAdmin() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            User user = new User();
            user.setId(2L);
            user.setRole("USER");
            when(userRepository.findBasicById(2L)).thenReturn(user);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> databaseHealthService.listFamilyMembers(10L));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
            verify(familyMemberRepository, never()).findMemberViewsByFamilyId(anyLong());
        }
    }

    @Test
    void listFamilyMembers_returnsAdminMemberViews() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(userRepository.findBasicById(1L)).thenReturn(adminUser());
            Family family = new Family();
            family.setId(10L);
            family.setName("Smith Family");
            when(familyRepository.selectById(10L)).thenReturn(family);
            when(familyMemberRepository.findMemberViewsByFamilyId(10L)).thenReturn(List.of(
                    FamilyMemberVO.builder().userId(8L).username("owner").role("OWNER").build(),
                    FamilyMemberVO.builder().userId(9L).username("member").role("MEMBER").build()
            ));

            List<FamilyMemberVO> members = databaseHealthService.listFamilyMembers(10L);

            assertEquals(2, members.size());
            assertEquals(8L, members.get(0).getUserId());
            assertEquals("OWNER", members.get(0).getRole());
        }
    }

    private static User adminUser() {
        User user = new User();
        user.setId(1L);
        user.setRole("ADMIN");
        return user;
    }

    private static FamilyMember member(Long familyId, Long userId) {
        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setUserId(userId);
        member.setRole("MEMBER");
        return member;
    }
}
