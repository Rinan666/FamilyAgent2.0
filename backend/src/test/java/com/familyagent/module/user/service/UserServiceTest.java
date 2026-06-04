package com.familyagent.module.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.user.dto.LoginRequest;
import com.familyagent.module.user.dto.LoginResponse;
import com.familyagent.module.user.dto.RegisterRequest;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserService userService;

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // ============================================
    // Register Tests
    // ============================================

    @Test
    void register_shouldCreateUserWithBCryptHash() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("mypassword123");

        when(userRepository.countByUsername("testuser")).thenReturn(0);

        userService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insert(captor.capture());
        User saved = captor.getValue();

        assertEquals("testuser", saved.getUsername());
        // BCrypt hash 非明文，以 $2a$ 开头
        assertTrue(saved.getPasswordHash().startsWith("$2a$"));
        // 双重验证：用 encoder.matches 确认 hash 与原文匹配
        assertTrue(encoder.matches("mypassword123", saved.getPasswordHash()));
    }

    @Test
    void register_shouldThrowWhenUsernameExists() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("existing");
        req.setPassword("pass");

        when(userRepository.countByUsername("existing")).thenReturn(1);

        assertThrows(BusinessException.class, () -> userService.register(req));
        verify(userRepository, never()).insert(any());
    }

    @Test
    void register_shouldUseNicknameOrDefault() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setPassword("pass");

        when(userRepository.countByUsername("alice")).thenReturn(0);

        userService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insert(captor.capture());
        // 未提供 nickname 时默认为 username
        assertEquals("alice", captor.getValue().getNickname());
    }

    @Test
    void register_shouldSetRoleUserAndStatusActive() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newbie");
        req.setPassword("pass");

        when(userRepository.countByUsername("newbie")).thenReturn(0);

        userService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insert(captor.capture());
        assertEquals("USER", captor.getValue().getRole());
        assertEquals("ACTIVE", captor.getValue().getStatus());
    }

    // ============================================
    // Login Tests
    // ============================================

    @Test
    void login_shouldSucceedWithCorrectPassword() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            User user = new User();
            user.setId(1L);
            user.setUsername("alice");
            user.setPasswordHash(encoder.encode("rightpassword"));
            user.setStatus("ACTIVE");

            when(userRepository.findByUsername("alice")).thenReturn(user);
            stpMock.when(() -> StpUtil.getTokenValue()).thenReturn("mock-token-abc");

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

        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("wrongpassword");

        assertThrows(BusinessException.class, () -> userService.login(req));
    }

    @Test
    void login_shouldFailWhenUserNotFound() {
        when(userRepository.findByUsername("nobody")).thenReturn(null);

        LoginRequest req = new LoginRequest();
        req.setUsername("nobody");
        req.setPassword("anything");

        assertThrows(BusinessException.class, () -> userService.login(req));
    }

    @Test
    void login_shouldFailWhenAccountDisabled() {
        User user = new User();
        user.setId(2L);
        user.setPasswordHash(encoder.encode("pass"));
        user.setStatus("DISABLED");

        when(userRepository.findByUsername("banned")).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setUsername("banned");
        req.setPassword("pass");

        assertThrows(BusinessException.class, () -> userService.login(req));
    }
}
