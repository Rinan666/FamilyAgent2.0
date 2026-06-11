package com.familyagent.module.user.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.SecureUtil;
import com.familyagent.common.constant.EntityStatus;
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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private boolean passwordMatches(String rawPassword, String storedHash) {
        if (storedHash.startsWith("$2a$")) {
            return encoder.matches(rawPassword, storedHash);
        }
        return SecureUtil.sha256(rawPassword).equals(storedHash);
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.countByUsername(request.getUsername()) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        InviteCode inviteCode = validateInviteCode(request.getInviteCode());
        consumeInviteCode(inviteCode.getCode());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(encoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole("USER");
        user.setStatus(EntityStatus.ACTIVE.name());
        user.setMetadata(Map.of(
                "inviteCode", inviteCode.getCode(),
                "inviteSource", inviteCode.getSource() == null ? "unknown" : inviteCode.getSource()
        ));

        try {
            userRepository.insert(user);
        } catch (DataIntegrityViolationException e) {
            if (isUsernameConflict(e)) {
                log.warn("Registration username conflict: username={}", request.getUsername(), e);
                throw new BusinessException(ErrorCode.USERNAME_EXISTS);
            }
            log.error("Registration persistence failed: username={}, inviteCode={}",
                    request.getUsername(), inviteCode.getCode(), e);
            throw new BusinessException(ErrorCode.DATA_PERSIST_FAILED);
        } catch (DataAccessException e) {
            log.error("Registration database access failed: username={}, inviteCode={}",
                    request.getUsername(), inviteCode.getCode(), e);
            throw e;
        }

        log.info("User registered: username={}, id={}", user.getUsername(), user.getId());
        return user;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (!EntityStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        if (!passwordMatches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        if (!user.getPasswordHash().startsWith("$2a$")) {
            user.setPasswordHash(encoder.encode(request.getPassword()));
            log.info("Password hash upgraded to BCrypt: username={}", user.getUsername());
        }

        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.updateById(user);

        log.info("User login succeeded: username={}, id={}", user.getUsername(), user.getId());

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
        user.setMetadata(normalizeMetadataValue(user.getMetadata()));
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
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "Current password is incorrect");
        }
        if (encoder.matches(request.getNewPassword(), current.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "New password must be different from current password");
        }
        current.setPasswordHash(encoder.encode(request.getNewPassword()));
        userRepository.updateById(current);
        StpUtil.logout(userId);
        log.info("Password changed: userId={}", userId);
    }

    private static String normalizeBirthDate(String birthDate) {
        if (birthDate == null || birthDate.trim().isEmpty()) {
            return null;
        }
        String trimmed = birthDate.trim();
        try {
            LocalDate parsed = LocalDate.parse(trimmed);
            if (parsed.isAfter(LocalDate.now()) || parsed.isBefore(LocalDate.now().minusYears(130))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Birth date is out of range");
            }
            return parsed.toString();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Birth date must use YYYY-MM-DD");
        }
    }

    private static Map<String, Object> toMutableMap(Object metadata) {
        Object normalized = normalizeMetadataValue(metadata);
        if (normalized == null) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> converted = objectMapper.convertValue(normalized, new TypeReference<>() {});
            return new LinkedHashMap<>(converted);
        } catch (IllegalArgumentException ignored) {
            return new LinkedHashMap<>();
        }
    }

    private static Object normalizeMetadataValue(Object metadata) {
        Object current = metadata;
        for (int depth = 0; depth < 5; depth++) {
            if (current == null) {
                return null;
            }
            if (current instanceof String text) {
                String trimmed = text.trim();
                if (trimmed.isEmpty()) {
                    return null;
                }
                try {
                    current = objectMapper.readValue(trimmed, Object.class);
                    continue;
                } catch (Exception ignored) {
                    return trimmed;
                }
            }
            if (current instanceof Map<?, ?> map
                    && map.size() == 1
                    && map.get("value") instanceof String nestedText) {
                current = nestedText;
                continue;
            }
            return current;
        }
        return current;
    }

    private static String metadataToJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Failed to save profile");
        }
    }

    private InviteCode validateInviteCode(String rawInviteCode) {
        if (rawInviteCode == null || rawInviteCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVITE_CODE_REQUIRED);
        }

        String normalizedCode = rawInviteCode.trim().toUpperCase();
        InviteCode inviteCode = inviteCodeRepository.findByCode(normalizedCode);
        if (inviteCode == null || !"ACTIVE".equals(inviteCode.getStatus())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        if (inviteCode.getExpiresAt() != null && !inviteCode.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        if (inviteCode.getMaxUses() != null && inviteCode.getUsedCount() != null
                && inviteCode.getUsedCount() >= inviteCode.getMaxUses()) {
            throw new BusinessException(ErrorCode.INVITE_CODE_EXHAUSTED);
        }
        return inviteCode;
    }

    private void consumeInviteCode(String normalizedCode) {
        int updated = inviteCodeRepository.incrementUsedCountByCode(normalizedCode);
        if (updated > 0) {
            return;
        }

        InviteCode latest = inviteCodeRepository.findByCode(normalizedCode);
        if (latest == null || !"ACTIVE".equals(latest.getStatus())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        if (latest.getExpiresAt() != null && !latest.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        throw new BusinessException(ErrorCode.INVITE_CODE_EXHAUSTED);
    }

    private boolean isUsernameConflict(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("users_username_key")
                        || (normalized.contains("users") && normalized.contains("username"))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
