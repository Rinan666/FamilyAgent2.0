package com.familyagent.module.user.facade;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.user.constant.UserRole;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserAccountAccessFacade {

    private final UserRepository userRepository;

    public Optional<UserAccountAccess> findById(Long userId) {
        User user = userRepository.findBasicById(userId);
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(new UserAccountAccess(userId, UserRole.ADMIN.matches(user.getRole())));
    }

    public UserAccountAccess requireCurrent() {
        Long userId = CurrentUserGuard.currentUserId();
        return findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
