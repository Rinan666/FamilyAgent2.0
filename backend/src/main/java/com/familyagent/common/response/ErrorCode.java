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
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    PARAM_VALID_FAILED(5001, "参数校验失败"),
    RATE_LIMIT_EXCEEDED(5002, "请求过于频繁"),

    // User codes: 1000-1999
    USER_NOT_FOUND(1001, "用户不存在"),
    USERNAME_EXISTS(1002, "用户名已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    ACCOUNT_DISABLED(1004, "账号已被禁用"),
    LOGIN_FAILED(1005, "登录失败"),
    INVITE_CODE_REQUIRED(1006, "请输入邀请码"),
    INVITE_CODE_INVALID(1007, "邀请码无效"),
    INVITE_CODE_EXHAUSTED(1008, "邀请码使用次数已达上限"),

    // Family codes: 2000-2999
    FAMILY_NOT_FOUND(2001, "家族不存在"),
    NOT_FAMILY_MEMBER(2002, "不是家族成员"),
    INSUFFICIENT_PERMISSION(2003, "家族权限不足"),
    FAMILY_FULL(2004, "家族人数已满"),
    INVALID_INVITE_CODE(2005, "邀请码无效"),
    ALREADY_MEMBER(2006, "已是家族成员"),

    // Question bank codes: 3000-3999
    QUESTION_NOT_FOUND(3001, "题目不存在"),
    QUESTION_DISABLED(3002, "题目已下架"),
    KP_NOT_FOUND(3003, "知识点不存在"),
    DUPLICATE_QUESTION(3004, "题目重复"),

    // AI service codes: 4000-4999
    AI_SERVICE_ERROR(4001, "AI服务异常"),
    AI_TIMEOUT(4002, "AI服务超时"),
    AI_QUALITY_LOW(4003, "AI输出质量不达标"),
    LLM_CALL_FAILED(4004, "大模型调用失败"),

    // Third-party service codes: 9000-9999
    OSS_UPLOAD_FAILED(9001, "文件上传失败"),
    MQ_SEND_FAILED(9002, "消息发送失败");

    private final int code;
    private final String message;
}
