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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务
 * <p>
 * 密码使用 BCrypt 加密（加盐 + 自适应密钥拉伸）。
 * 旧版 SHA-256 密码在登录时自动迁移到 BCrypt。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 验证密码：先 BCrypt，失败则尝试 SHA-256 迁移
     */
    private boolean passwordMatches(String rawPassword, String storedHash) {
        // BCrypt hash 以 $2a$ 开头
        if (storedHash.startsWith("$2a$")) {
            return encoder.matches(rawPassword, storedHash);
        }
        // 旧版 SHA-256：验证后自动升级
        String sha256 = SecureUtil.sha256(rawPassword);
        return sha256.equals(storedHash);
    }

    @Transactional
    public User register(RegisterRequest request) {
        // 检查用户名
        if (userRepository.countByUsername(request.getUsername()) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(encoder.encode(request.getPassword()));
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

        if (!passwordMatches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // 旧 SHA-256 hash 自动升级为 BCrypt
        if (!user.getPasswordHash().startsWith("$2a$")) {
            user.setPasswordHash(encoder.encode(request.getPassword()));
            log.info("密码已升级为BCrypt: username={}", user.getUsername());
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
