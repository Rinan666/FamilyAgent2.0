package com.familyagent.module.user.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.SecureUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.user.dto.LoginRequest;
import com.familyagent.module.user.dto.LoginResponse;
import com.familyagent.module.user.dto.RegisterRequest;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User register(RegisterRequest request) {
        // 检查用户名
        if (userRepository.countByUsername(request.getUsername()) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(SecureUtil.sha256(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole("USER");
        user.setStatus("ACTIVE");

        userRepository.insert(user);
        log.info("用户注册成功: username={}, id={}", user.getUsername(), user.getId());
        return user;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String passwordHash = SecureUtil.sha256(request.getPassword());
        if (!passwordHash.equals(user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // Sa-Token 登录
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.updateById(user);

        log.info("用户登录成功: username={}, id={}", user.getUsername(), user.getId());

        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .token(token)
                .tokenName("Authorization")
                .build();
    }

    public User getById(Long id) {
        User user = userRepository.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    public User getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        return getById(userId);
    }
}
