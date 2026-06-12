package com.familyagent.common.exception;

import java.util.Locale;

/** Detects whether a throwable chain represents a duplicate username constraint violation. */
public final class UsernameConflictDetector {

    private UsernameConflictDetector() {}

    public static boolean isUsernameConflict(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                boolean duplicateViolation = normalized.contains("duplicate key")
                        || normalized.contains("duplicate entry")
                        || normalized.contains("already exists")
                        || normalized.contains("unique constraint")
                        || normalized.contains("unique index")
                        || normalized.contains("unique violation");
                // Only match constraint names scoped to the users table to avoid
                // false positives from other tables that also have a username column.
                boolean usernameConstraint = normalized.contains("users_username_key")
                        || normalized.contains("constraint [users_username_key]")
                        || normalized.contains(" key (username)=")
                        || normalized.contains("for key 'users.username'")
                        || normalized.contains("for key `users.username`");
                if (duplicateViolation && usernameConstraint) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
