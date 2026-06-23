package com.familyagent.module.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.wechat.WeChatMiniAppClient;
import com.familyagent.module.user.dto.LoginResponse;
import com.familyagent.module.user.dto.WeChatLoginRequest;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Handles WeChat mini app login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatLoginService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final String DEFAULT_NICKNAME = "WeChat User";

    private final UserRepository userRepository;
    private final WeChatMiniAppClient weChatMiniAppClient;

    @Transactional
    public LoginResponse login(WeChatLoginRequest request) {
        WeChatMiniAppClient.SessionInfo sessionInfo = weChatMiniAppClient.exchangeCodeForSession(request.getCode());
        User user = userRepository.findByWechatOpenId(sessionInfo.openId());
        if (user == null) {
            user = createWeChatUser(sessionInfo.openId(), request);
        } else {
            refreshProfile(user, request);
        }

        if (!EntityStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.updateById(user);
        log.info("WeChat login succeeded: userId={}", user.getId());

        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .metadata(user.getMetadata())
                .token(token)
                .tokenName("Authorization")
                .build();
    }

    private User createWeChatUser(String openId, WeChatLoginRequest request) {
        User user = new User();
        user.setUsername(buildUsername(openId));
        user.setWechatOpenId(openId);
        user.setPasswordHash(PASSWORD_ENCODER.encode(UUID.randomUUID().toString()));
        user.setNickname(defaultIfBlank(request.getNickname(), DEFAULT_NICKNAME));
        user.setAvatarUrl(blankToNull(request.getAvatarUrl()));
        user.setRole("USER");
        user.setStatus(EntityStatus.ACTIVE.name());
        user.setMetadata(Map.of());

        try {
            userRepository.insert(user);
            return user;
        } catch (DataIntegrityViolationException ex) {
            User existing = userRepository.findByWechatOpenId(openId);
            if (existing != null) {
                refreshProfile(existing, request);
                return existing;
            }
            throw new BusinessException(ErrorCode.DATA_PERSIST_FAILED, "Failed to create WeChat user");
        }
    }

    private void refreshProfile(User user, WeChatLoginRequest request) {
        String nickname = blankToNull(request.getNickname());
        String avatarUrl = blankToNull(request.getAvatarUrl());
        if (nickname != null && !nickname.equals(user.getNickname())) {
            user.setNickname(nickname);
        }
        if (avatarUrl != null && !avatarUrl.equals(user.getAvatarUrl())) {
            user.setAvatarUrl(avatarUrl);
        }
    }

    private static String buildUsername(String openId) {
        String prefix = openId.substring(0, Math.min(openId.length(), 24));
        String suffix = Integer.toHexString(openId.hashCode()).replace("-", "0");
        return "wx_" + prefix + "_" + suffix;
    }

    private static String defaultIfBlank(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
