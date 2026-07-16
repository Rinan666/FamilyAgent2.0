package com.familyagent.module.skillrun.service;

import com.familyagent.common.constant.SkillRunStatus;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SkillRunInputPolicy {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_SUMMARY_LENGTH = 2000;
    private static final String DEFAULT_SOURCE = "FAMILY_AGENT";

    public String normalizeSkillName(String value) {
        String name = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (name.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "技能名称不能为空");
        }
        if (!name.matches("[a-z0-9_\\-]{2,80}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "技能名称格式不正确");
        }
        return name;
    }

    public String normalizeStatus(String value, SkillRunStatus fallback) {
        try {
            return SkillRunStatus.parse(value, fallback).name();
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的技能运行状态");
        }
    }

    public int normalizeLimit(int limit) {
        return limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    public String normalizeSource(String value) {
        return value == null || value.isBlank() ? DEFAULT_SOURCE : value.trim();
    }

    public String normalizeSummary(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() <= MAX_SUMMARY_LENGTH ? text : text.substring(0, MAX_SUMMARY_LENGTH);
    }
}
