package com.familyagent.module.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.invite.facade.RegistrationInviteDetails;
import com.familyagent.module.invite.facade.RegistrationInviteFacade;
import com.familyagent.module.user.dto.LoginRequest;
import com.familyagent.module.user.dto.LoginResponse;
import com.familyagent.module.user.dto.RegisterRequest;
import com.familyagent.module.user.dto.UpdateProfileRequest;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RegistrationInviteFacade registrationInviteFacade;
    @Mock private RedissonClient redissonClient;
    @Mock private RAtomicLong loginFailureCounter;
    @InjectMocks private UserService userService;

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUpLoginRateLimit() {
        lenient().when(redissonClient.getAtomicLong(anyString())).thenReturn(loginFailureCounter);
    }

    @Test
    void register_shouldCreateUserWithBCryptHash() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("mypassword123");
        req.setInviteCode("ASDFGZXCVB");

        when(userRepository.countByUsername("testuser")).thenReturn(0);
        mockInviteCode("ASDFGZXCVB");

        userService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insert(captor.capture());
        User saved = captor.getValue();

        assertEquals("testuser", saved.getUsername());
        assertTrue(saved.getPasswordHash().startsWith("$2a$"));
        assertTrue(encoder.matches("mypassword123", saved.getPasswordHash()));
    }

    @Test
    void register_shouldThrowWhenUsernameExists() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("existing");
        req.setPassword("pass123");
        req.setInviteCode("ASDFGZXCVB");

        when(userRepository.countByUsername("existing")).thenReturn(1);

        assertThrows(BusinessException.class, () -> userService.register(req));
        verify(userRepository, never()).insert(any());
    }

    @Test
    void register_shouldTranslateInsertUsernameConflict() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("existing");
        req.setPassword("pass123");
        req.setInviteCode("ASDFGZXCVB");

        when(userRepository.countByUsername("existing")).thenReturn(0);
        mockInviteCode("ASDFGZXCVB");
        doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint users_username_key"))
                .when(userRepository).insert(any(User.class));

        BusinessException error = assertThrows(BusinessException.class, () -> userService.register(req));

        assertEquals(ErrorCode.USERNAME_EXISTS.getCode(), error.getCode());
    }

    @Test
    void register_shouldNotTranslateNonDuplicateUsernameMentionIntoConflict() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("existing");
        req.setPassword("pass123");
        req.setInviteCode("ASDFGZXCVB");

        when(userRepository.countByUsername("existing")).thenReturn(0);
        mockInviteCode("ASDFGZXCVB");
        doThrow(new DataIntegrityViolationException(
                "PreparedStatementCallback; SQL [select u.username from users u]; numeric value out of range for column username"))
                .when(userRepository).insert(any(User.class));

        BusinessException error = assertThrows(BusinessException.class, () -> userService.register(req));

        assertEquals(ErrorCode.DATA_PERSIST_FAILED.getCode(), error.getCode());
    }

    @Test
    void register_shouldRethrowInsertDatabaseAccessFailure() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("existing");
        req.setPassword("pass123");
        req.setInviteCode("ASDFGZXCVB");

        when(userRepository.countByUsername("existing")).thenReturn(0);
        mockInviteCode("ASDFGZXCVB");

        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("db offline");
        doThrow(failure).when(userRepository).insert(any(User.class));

        DataAccessResourceFailureException thrown = assertThrows(DataAccessResourceFailureException.class,
                () -> userService.register(req));

        assertSame(failure, thrown);
    }

    @Test
    void register_shouldRethrowInviteCodeDatabaseAccessFailure() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newbie");
        req.setPassword("pass123");
        req.setInviteCode("ASDFGZXCVB");

        when(userRepository.countByUsername("newbie")).thenReturn(0);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("db offline");
        when(registrationInviteFacade.consume("ASDFGZXCVB")).thenThrow(failure);

        DataAccessResourceFailureException thrown = assertThrows(DataAccessResourceFailureException.class,
                () -> userService.register(req));

        assertSame(failure, thrown);
        verify(userRepository, never()).insert(any());
    }

    @Test
    void register_shouldUseNicknameOrDefault() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setPassword("pass123");
        req.setInviteCode("ASDFGZXCVB");

        when(userRepository.countByUsername("alice")).thenReturn(0);
        mockInviteCode("ASDFGZXCVB");

        userService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insert(captor.capture());
        assertEquals("alice", captor.getValue().getNickname());
    }

    @Test
    void register_shouldSetRoleStatusAndInviteMetadata() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newbie");
        req.setPassword("pass123");
        req.setInviteCode("ASDFGZXCVB");

        when(userRepository.countByUsername("newbie")).thenReturn(0);
        mockInviteCode("ASDFGZXCVB");

        userService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insert(captor.capture());
        assertEquals("USER", captor.getValue().getRole());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertEquals(Map.of("inviteCode", "ASDFGZXCVB", "inviteSource", "seed-test"), captor.getValue().getMetadata());
    }

    @Test
    void register_shouldRejectMissingInviteCode() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newbie");
        req.setPassword("pass123");

        when(userRepository.countByUsername("newbie")).thenReturn(0);
        when(registrationInviteFacade.consume(null))
                .thenThrow(new BusinessException(ErrorCode.INVITE_CODE_REQUIRED));

        BusinessException error = assertThrows(BusinessException.class, () -> userService.register(req));

        assertEquals(ErrorCode.INVITE_CODE_REQUIRED.getCode(), error.getCode());
        verify(userRepository, never()).insert(any());
    }

    @Test
    void register_shouldRejectExhaustedInviteCode() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newbie");
        req.setPassword("pass123");
        req.setInviteCode("ASDFGZXCVB");

        when(userRepository.countByUsername("newbie")).thenReturn(0);
        when(registrationInviteFacade.consume("ASDFGZXCVB"))
                .thenThrow(new BusinessException(ErrorCode.INVITE_CODE_EXHAUSTED));

        BusinessException error = assertThrows(BusinessException.class, () -> userService.register(req));

        assertEquals(ErrorCode.INVITE_CODE_EXHAUSTED.getCode(), error.getCode());
        verify(userRepository, never()).insert(any());
    }

    @Test
    void login_shouldSucceedWithCorrectPassword() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            User user = new User();
            user.setId(1L);
            user.setUsername("alice");
            user.setPasswordHash(encoder.encode("rightpassword"));
            user.setStatus("ACTIVE");

            when(userRepository.findByUsername("alice")).thenReturn(user);
            stpMock.when(StpUtil::getTokenValue).thenReturn("mock-token-abc");

            LoginRequest req = new LoginRequest();
            req.setUsername("alice");
            req.setPassword("rightpassword");

            LoginResponse resp = userService.login(req);

            assertNotNull(resp);
            assertEquals(1L, resp.getUserId());
            assertEquals("alice", resp.getUsername());
            assertEquals("mock-token-abc", resp.getToken());
            assertEquals("Authorization", resp.getTokenName());
            stpMock.verify(() -> StpUtil.login(1L));
            verify(userRepository).updateById(any(User.class));
        }
    }

    @Test
    void login_shouldFailWithWrongPassword() {
        User user = new User();
        user.setId(1L);
        user.setPasswordHash(encoder.encode("rightpassword"));
        user.setStatus("ACTIVE");

        when(userRepository.findByUsername("alice")).thenReturn(user);
        when(loginFailureCounter.incrementAndGet()).thenReturn(1L);

        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("wrongpassword");

        BusinessException error = assertThrows(BusinessException.class, () -> userService.login(req));

        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), error.getCode());
        verify(loginFailureCounter).expire(15, TimeUnit.MINUTES);
    }

    @Test
    void login_shouldFailWhenUserNotFound() {
        when(userRepository.findByUsername("nobody")).thenReturn(null);
        when(loginFailureCounter.incrementAndGet()).thenReturn(2L);

        LoginRequest req = new LoginRequest();
        req.setUsername("nobody");
        req.setPassword("anything");

        BusinessException error = assertThrows(BusinessException.class, () -> userService.login(req));

        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), error.getCode());
    }

    @Test
    void login_shouldRateLimitRepeatedFailures() {
        when(loginFailureCounter.get()).thenReturn(10L);

        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("anything");

        BusinessException error = assertThrows(BusinessException.class, () -> userService.login(req));

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(), error.getCode());
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void login_shouldFailWhenAccountDisabled() {
        User user = new User();
        user.setId(2L);
        user.setPasswordHash(encoder.encode("pass123"));
        user.setStatus("DISABLED");

        when(userRepository.findByUsername("banned")).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setUsername("banned");
        req.setPassword("pass123");

        assertThrows(BusinessException.class, () -> userService.login(req));
    }

    @Test
    void getById_shouldUnwrapNestedJsonMetadata() {
        User user = new User();
        user.setId(29L);
        user.setMetadata("\"{\\\"birthDate\\\":\\\"2005-09-22\\\",\\\"birthYear\\\":\\\"2005\\\"}\"");
        when(userRepository.findBasicById(29L)).thenReturn(user);

        User result = userService.getById(29L);

        assertInstanceOf(Map.class, result.getMetadata());
        Map<?, ?> metadata = (Map<?, ?>) result.getMetadata();
        assertEquals("2005-09-22", metadata.get("birthDate"));
        assertEquals("2005", metadata.get("birthYear"));
    }

    @Test
    void updateProfile_shouldPreserveNestedJsonMetadataWhenSavingBirthDate() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            User current = new User();
            current.setId(35L);
            current.setMetadata("\"{\\\"inviteCode\\\":\\\"FAMILY006\\\",\\\"inviteSource\\\":\\\"seed-family-006\\\"}\"");

            User persisted = new User();
            persisted.setId(35L);
            persisted.setMetadata(Map.of(
                    "inviteCode", "FAMILY006",
                    "inviteSource", "seed-family-006",
                    "birthDate", "2006-01-02",
                    "birthYear", "2006"
            ));

            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(35L);
            when(userRepository.findBasicById(35L)).thenReturn(current, persisted);

            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setBirthDate("2006-01-02");

            User result = userService.updateProfile(request);

            assertInstanceOf(Map.class, result.getMetadata());
            Map<?, ?> metadata = (Map<?, ?>) result.getMetadata();
            assertEquals("2006-01-02", metadata.get("birthDate"));
            assertEquals("FAMILY006", metadata.get("inviteCode"));
            verify(userRepository).updateMetadata(eq(35L), contains("\"inviteCode\":\"FAMILY006\""));
            verify(userRepository).updateMetadata(eq(35L), contains("\"birthDate\":\"2006-01-02\""));
        }
    }

    private void mockInviteCode(String code) {
        when(registrationInviteFacade.consume(code))
                .thenReturn(new RegistrationInviteDetails(code, "seed-test"));
    }
}
