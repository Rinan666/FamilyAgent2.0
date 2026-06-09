package com.familyagent.module.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticRequest;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticResponse;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.repository.FamilyMemberRepository;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock private FamilyMemberRepository familyMemberRepository;
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

            verify(jdbcTemplate).update("UPDATE families SET created_by = NULL WHERE created_by = ?", 88L);
            verify(jdbcTemplate).update("DELETE FROM family_relationships WHERE from_user_id = ?", 88L);
            verify(jdbcTemplate).update("DELETE FROM memory_entry_votes WHERE user_id = ?", 88L);
            verify(jdbcTemplate).update("DELETE FROM chat_sessions WHERE user_id = ?", 88L);
            verify(jdbcTemplate).update("DELETE FROM family_members WHERE user_id = ?", 88L);
            verify(jdbcTemplate).update("DELETE FROM users WHERE id = ?", 88L);
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
