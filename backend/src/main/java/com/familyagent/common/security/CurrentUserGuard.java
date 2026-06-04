package com.familyagent.common.security;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;

public final class CurrentUserGuard {

    private CurrentUserGuard() {
    }

    public static Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    public static void requireSelf(Long userId) {
        if (userId == null || !currentUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能访问当前登录用户的数据");
        }
    }
}
