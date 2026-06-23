package com.familyagent.module.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.wechat.WeChatMiniAppClient;
import com.familyagent.module.user.dto.LoginResponse;
import com.familyagent.module.user.dto.WeChatLoginRequest;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeChatLoginServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WeChatMiniAppClient weChatMiniAppClient;
    @InjectMocks private WeChatLoginService weChatLoginService;

    @Test
    void login_shouldCreateNewWeChatUser() {
        WeChatLoginRequest request = request("code-1", "Alice", "https://avatar.example.com/a.png");
        when(weChatMiniAppClient.exchangeCodeForSession("code-1"))
                .thenReturn(new WeChatMiniAppClient.SessionInfo("openid-1", "session-1"));
        when(userRepository.findByWechatOpenId("openid-1")).thenReturn(null);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getTokenValue).thenReturn("token-1");

            LoginResponse response = weChatLoginService.login(request);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).insert(captor.capture());
            User saved = captor.getValue();
            assertEquals("openid-1", saved.getWechatOpenId());
            assertEquals("Alice", saved.getNickname());
            assertTrue(saved.getPasswordHash().startsWith("$2a$"));
            assertEquals("token-1", response.getToken());
        }
    }

    @Test
    void login_shouldRefreshExistingProfile() {
        WeChatLoginRequest request = request("code-2", "New Nick", "https://avatar.example.com/new.png");
        User existing = new User();
        existing.setId(42L);
        existing.setUsername("wx_old");
        existing.setWechatOpenId("openid-2");
        existing.setPasswordHash("$2a$hash");
        existing.setNickname("Old Nick");
        existing.setAvatarUrl("https://avatar.example.com/old.png");
        existing.setRole("USER");
        existing.setStatus("ACTIVE");

        when(weChatMiniAppClient.exchangeCodeForSession("code-2"))
                .thenReturn(new WeChatMiniAppClient.SessionInfo("openid-2", "session-2"));
        when(userRepository.findByWechatOpenId("openid-2")).thenReturn(existing);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getTokenValue).thenReturn("token-2");

            LoginResponse response = weChatLoginService.login(request);

            verify(userRepository).updateById(existing);
            assertEquals("New Nick", existing.getNickname());
            assertEquals("https://avatar.example.com/new.png", existing.getAvatarUrl());
            assertEquals(42L, response.getUserId());
        }
    }

    @Test
    void login_shouldRejectDisabledAccounts() {
        WeChatLoginRequest request = request("code-3", "Alice", null);
        User existing = new User();
        existing.setId(7L);
        existing.setWechatOpenId("openid-3");
        existing.setPasswordHash("$2a$hash");
        existing.setRole("USER");
        existing.setStatus("DISABLED");

        when(weChatMiniAppClient.exchangeCodeForSession("code-3"))
                .thenReturn(new WeChatMiniAppClient.SessionInfo("openid-3", "session-3"));
        when(userRepository.findByWechatOpenId("openid-3")).thenReturn(existing);

        BusinessException error = assertThrows(BusinessException.class,
                () -> weChatLoginService.login(request));

        assertEquals(ErrorCode.ACCOUNT_DISABLED.getCode(), error.getCode());
    }

    private static WeChatLoginRequest request(String code, String nickname, String avatarUrl) {
        WeChatLoginRequest request = new WeChatLoginRequest();
        request.setCode(code);
        request.setNickname(nickname);
        request.setAvatarUrl(avatarUrl);
        return request;
    }
}
