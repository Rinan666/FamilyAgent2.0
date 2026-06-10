package com.familyagent.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Standard error codes.
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // System-wide codes: 5000-5999
    SUCCESS(200, "Success"),
    BAD_REQUEST(400, "Invalid request parameters"),
    UNAUTHORIZED(401, "Not signed in or session expired"),
    FORBIDDEN(403, "Access denied"),
    NOT_FOUND(404, "Resource not found"),
    INTERNAL_ERROR(500, "Internal server error"),
    PARAM_VALID_FAILED(5001, "Parameter validation failed"),
    RATE_LIMIT_EXCEEDED(5002, "Too many requests"),
    DATABASE_ACCESS_ERROR(5003, "Database access error"),
    DATA_PERSIST_FAILED(5004, "Failed to save data"),

    // User codes: 1000-1999
    USER_NOT_FOUND(1001, "User not found"),
    USERNAME_EXISTS(1002, "Username already exists"),
    PASSWORD_ERROR(1003, "Incorrect password"),
    ACCOUNT_DISABLED(1004, "Account has been disabled"),
    LOGIN_FAILED(1005, "Login failed"),
    INVITE_CODE_REQUIRED(1006, "Please enter an invite code"),
    INVITE_CODE_INVALID(1007, "Invalid invite code"),
    INVITE_CODE_EXHAUSTED(1008, "Invite code usage limit reached"),

    // Family codes: 2000-2999
    FAMILY_NOT_FOUND(2001, "Family not found"),
    NOT_FAMILY_MEMBER(2002, "Not a family member"),
    INSUFFICIENT_PERMISSION(2003, "Insufficient family permissions"),
    FAMILY_FULL(2004, "Family is full"),
    INVALID_INVITE_CODE(2005, "Invalid invite code"),
    ALREADY_MEMBER(2006, "Already a family member"),

    // Question bank codes: 3000-3999
    QUESTION_NOT_FOUND(3001, "Question not found"),
    QUESTION_DISABLED(3002, "Question is unavailable"),
    KP_NOT_FOUND(3003, "Knowledge point not found"),
    DUPLICATE_QUESTION(3004, "Duplicate question"),

    // AI service codes: 4000-4999
    AI_SERVICE_ERROR(4001, "AI service error"),
    AI_TIMEOUT(4002, "AI service timeout"),
    AI_QUALITY_LOW(4003, "AI output quality is too low"),
    LLM_CALL_FAILED(4004, "Large model call failed"),

    // Third-party service codes: 9000-9999
    OSS_UPLOAD_FAILED(9001, "File upload failed"),
    MQ_SEND_FAILED(9002, "Message send failed");

    private final int code;
    private final String message;
}
