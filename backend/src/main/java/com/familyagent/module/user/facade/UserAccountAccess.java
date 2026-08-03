package com.familyagent.module.user.facade;

public record UserAccountAccess(Long userId, boolean platformAdmin) {

    public UserAccountAccess(boolean platformAdmin) {
        this(null, platformAdmin);
    }
}
