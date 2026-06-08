package com.familyagent.module.user.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.SecureUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.invite.entity.InviteCode;
import com.familyagent.module.invite.repository.InviteCodeRepository;
import com.familyagent.module.user.dto.ChangePasswordRequest;
import com.familyagent.module.user.dto.LoginRequest;
import com.familyagent.module.user.dto.LoginResponse;
import com.familyagent.module.user.dto.RegisterRequest;
import com.familyagent.module.user.dto.UpdateProfileRequest;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final InviteCodeRepository inviteCodeRepository;
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        String inviteCodeValue = normalizeInviteCode(request.getInviteCode());
        InviteCode inviteCode = validateInviteCode(inviteCodeValue);

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
        user.setMetadata(Map.of(
                "inviteCode", inviteCode.getCode(),
                "inviteSource", inviteCode.getSource() == null ? "" : inviteCode.getSource()
        ));

        userRepository.insert(user);
        int updated = inviteCodeRepository.incrementUsedCount(inviteCode.getId());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVITE_CODE_EXHAUSTED);
        }
        log.info("用户注册成功: username={}, id={}", user.getUsername(), user.getId());
        return user;
    }

    private String normalizeInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVITE_CODE_REQUIRED);
        }
        return inviteCode.trim().toUpperCase();
    }

    private InviteCode validateInviteCode(String inviteCodeValue) {
        InviteCode inviteCode = inviteCodeRepository.findByCode(inviteCodeValue);
        if (inviteCode == null || !"ACTIVE".equals(inviteCode.getStatus())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        if (inviteCode.getExpiresAt() != null && inviteCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID, "邀请码已过期");
        }
        if (inviteCode.getUsedCount() != null
                && inviteCode.getMaxUses() != null
                && inviteCode.getUsedCount() >= inviteCode.getMaxUses()) {
            throw new BusinessException(ErrorCode.INVITE_CODE_EXHAUSTED);
        }
        return inviteCode;
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
                .role(user.getRole())
                .status(user.getStatus())
                .metadata(user.getMetadata())
                .token(token)
                .tokenName("Authorization")
                .build();
    }

    public User getById(Long id) {
        User user = userRepository.findBasicById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    public User getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        return getById(userId);
    }

    @Transactional
    public User updateProfile(UpdateProfileRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        User current = userRepository.findBasicById(userId);
        if (current == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Map<String, Object> metadata = toMutableMap(current.getMetadata());
        String birthDate = normalizeBirthDate(request.getBirthDate());
        if (birthDate == null) {
            metadata.remove("birthDate");
            metadata.remove("birthday");
            metadata.remove("dateOfBirth");
            metadata.remove("birthYear");
            metadata.remove("yearOfBirth");
        } else {
            metadata.put("birthDate", birthDate);
            metadata.put("birthYear", birthDate.substring(0, 4));
        }

        current.setMetadata(metadata);
        userRepository.updateMetadata(userId, metadataToJson(metadata));
        return getById(userId);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        User current = userRepository.findByIdWithPassword(userId);
        if (current == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordMatches(request.getCurrentPassword(), current.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "当前密码不正确");
        }
        if (encoder.matches(request.getNewPassword(), current.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        current.setPasswordHash(encoder.encode(request.getNewPassword()));
        userRepository.updateById(current);
        StpUtil.logout(userId);
        log.info("用户修改密码成功: userId={}", userId);
    }

    private static String normalizeBirthDate(String birthDate) {
        if (birthDate == null || birthDate.trim().isEmpty()) {
            return null;
        }
        String trimmed = birthDate.trim();
        try {
            LocalDate parsed = LocalDate.parse(trimmed);
            if (parsed.isAfter(LocalDate.now()) || parsed.isBefore(LocalDate.now().minusYears(130))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "出生日期不在合理范围内");
            }
            return parsed.toString();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "出生日期格式应为 YYYY-MM-DD");
        }
    }

    private static Map<String, Object> toMutableMap(Object metadata) {
        if (metadata == null) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> converted = objectMapper.convertValue(metadata, new TypeReference<>() {});
            return new LinkedHashMap<>(converted);
        } catch (IllegalArgumentException ignored) {
            return new LinkedHashMap<>();
        }
    }

    private static String metadataToJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "个人资料保存失败");
        }
    }
}
