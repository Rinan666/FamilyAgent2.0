package com.familyagent.module.admin.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.user.facade.UserAccountAccess;
import com.familyagent.module.user.facade.UserAccountAccessFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PlatformAdminAccessSupport {

    private final UserAccountAccessFacade userAccountAccessFacade;

    void requirePlatformAdmin() {
        Long userId = CurrentUserGuard.currentUserId();
        boolean platformAdmin = userAccountAccessFacade.findById(userId)
                .map(UserAccountAccess::platformAdmin)
                .orElse(false);
        if (!platformAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Platform admin permission is required");
        }
    }
}
