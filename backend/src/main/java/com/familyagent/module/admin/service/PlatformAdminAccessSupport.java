package com.familyagent.module.admin.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;

final class PlatformAdminAccessSupport {

    private final UserRepository userRepository;

    PlatformAdminAccessSupport(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    void requirePlatformAdmin() {
        Long userId = CurrentUserGuard.currentUserId();
        User user = userRepository.findBasicById(userId);
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Platform admin permission is required");
        }
    }
}
