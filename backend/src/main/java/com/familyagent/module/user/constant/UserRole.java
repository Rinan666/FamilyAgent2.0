package com.familyagent.module.user.constant;

public enum UserRole {
    USER,
    ADMIN;

    public boolean matches(String value) {
        return value != null && name().equalsIgnoreCase(value.trim());
    }
}
