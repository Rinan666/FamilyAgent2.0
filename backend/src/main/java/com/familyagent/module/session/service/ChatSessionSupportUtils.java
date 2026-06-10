package com.familyagent.module.session.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.module.session.dto.ChatSessionMessagePayload;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.entity.ChatSessionMessage;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ChatSessionSupportUtils {

    private ChatSessionSupportUtils() {
    }

    static List<ChatSessionMessagePayload> parseLegacyMessages(Object rawMessages, ObjectMapper objectMapper) {
        if (rawMessages == null) {
            return List.of();
        }
        List<?> list = objectMapper.convertValue(rawMessages, List.class);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<ChatSessionMessagePayload> payloads = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {});
            ChatSessionMessagePayload payload = new ChatSessionMessagePayload();
            payload.setId(stringValue(map.get("id"), null));
            payload.setRole(stringValue(map.get("role"), "user"));
            payload.setContent(stringValue(map.get("content"), ""));
            payload.setTimestamp(stringValue(map.get("timestamp"), null));
            Object metadata = map.get("metadata");
            if (metadata instanceof Map<?, ?> nested) {
                payload.setMetadata(new LinkedHashMap<>(castMap(nested)));
            }
            payloads.add(payload);
        }
        return payloads;
    }

    static List<ChatSessionMessagePayload> normalizePayloads(List<ChatSessionMessagePayload> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ChatSessionMessagePayload> normalized = new ArrayList<>();
        for (ChatSessionMessagePayload message : messages) {
            if (message == null) {
                continue;
            }
            ChatSessionMessagePayload copy = new ChatSessionMessagePayload();
            copy.setId(blankToNull(message.getId()));
            copy.setRole(normalizeRole(message.getRole()));
            copy.setContent(message.getContent() == null ? "" : message.getContent());
            copy.setTimestamp(blankToNull(message.getTimestamp()));
            copy.setToolName(blankToNull(message.getToolName()));
            copy.setTokenCount(message.getTokenCount() == null ? 0 : Math.max(message.getTokenCount(), 0));
            copy.setMetadata(message.getMetadata() == null ? Map.of() : new LinkedHashMap<>(message.getMetadata()));
            normalized.add(copy);
        }
        return normalized;
    }

    static String deriveTitle(ChatSession session, List<ChatSessionMessage> messages) {
        String existing = blankToNull(session.getTitle());
        if (existing != null) {
            return existing;
        }
        Map<String, Object> metadata = castMap(session.getMetadata());
        Object questionContent = metadata.get("questionContent");
        if (questionContent instanceof Map<?, ?> map) {
            String stem = blankToNull(String.valueOf(map.get("stem")));
            if (stem != null) {
                return truncate(stem, 36);
            }
        } else if (questionContent instanceof String stem) {
            String title = blankToNull(stem);
            if (title != null) {
                return truncate(title, 36);
            }
        }
        for (ChatSessionMessage message : messages) {
            if ("user".equalsIgnoreCase(message.getRole()) && blankToNull(message.getContent()) != null) {
                return truncate(message.getContent().trim(), 36);
            }
        }
        return truncate(blankToNull(session.getSummary()) == null ? "未命名会话" : session.getSummary(), 36);
    }

    static LocalDateTime resolveLastMessageAt(List<ChatSessionMessage> messages, ChatSession session) {
        if (!messages.isEmpty()) {
            ChatSessionMessage last = messages.get(messages.size() - 1);
            if (last.getCreatedAt() != null) {
                return last.getCreatedAt();
            }
        }
        return session.getLastMessageAt() == null ? session.getStartedAt() : session.getLastMessageAt();
    }

    static String buildRollingSummary(List<ChatSessionMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index -= 1) {
            String content = blankToNull(messages.get(index).getContent());
            if (content != null) {
                return truncate(content, 120);
            }
        }
        return null;
    }

    static Map<String, Object> withStorageVersion(Map<String, Object> metadata, int storageVersion) {
        metadata.put("storageVersion", storageVersion);
        return metadata;
    }

    static int normalizeSessionLimit(int limit, int defaultLimit, int maxLimit) {
        if (limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }

    static int normalizeMessagePageSize(int limit, int defaultLimit, int maxLimit) {
        if (limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }

    static String normalizeRole(String role) {
        String normalized = role == null ? "user" : role.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "assistant", "system", "user" -> normalized;
            default -> "user";
        };
    }

    static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxLength));
    }

    static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    static Map<String, Object> castMap(Object value) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> copy.put(String.valueOf(key), item));
        }
        return copy;
    }

    static Map<String, Object> toMutableMap(Object value) {
        return castMap(value);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static LocalDateTime parseTimestamp(String value, LocalDateTime fallback) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return fallback == null ? LocalDateTime.now() : fallback;
        }
        try {
            return OffsetDateTime.parse(normalized).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Try local date-time below.
        }
        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            return fallback == null ? LocalDateTime.now() : fallback;
        }
    }
}
