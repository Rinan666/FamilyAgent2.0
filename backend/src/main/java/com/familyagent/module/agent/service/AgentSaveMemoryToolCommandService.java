package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.dto.AgentSaveMemoryToolRequest;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentToolExecutor;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.dto.AgentToolCallRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.harness.dto.CreateDiaryEntryInput;
import com.familyagent.module.agent.harness.dto.CreateFamilyMemoryInput;
import com.familyagent.module.agent.harness.dto.CreateGrowthGuardRecordInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentSaveMemoryToolCommandService {

    private final AgentToolExecutor toolExecutor;

    public AgentToolCallResult<?> requestSave(AgentSaveMemoryToolRequest request, Long viewerUserId) {
        SaveWriteCategory category = SaveWriteCategory.from(request.getWriteCategory());
        requireGrowthTarget(category, request);
        AgentRunContext context = new AgentRunContext(
                requestId(request.getRequestId()),
                request.getFamilyId(),
                viewerUserId,
                request.getSessionId(),
                defaultText(request.getAgentMode(), "family"),
                defaultText(request.getSubject(), "FamilyAgent"),
                defaultText(request.getContextLabel(), "save_memory"));

        return switch (category) {
            case RECORD -> toolExecutor.execute(new AgentToolCallRequest<>(
                    AgentToolName.CREATE_DIARY_ENTRY.value(),
                    context,
                    new CreateDiaryEntryInput(
                            request.getContent(),
                            request.getDiaryEntryType(),
                            request.getTitle(),
                            null,
                            tags(request.getTags()),
                            request.getVisibility(),
                            request.getMetadata())));
            case EXPERIENCE -> toolExecutor.execute(new AgentToolCallRequest<>(
                    AgentToolName.CREATE_FAMILY_MEMORY.value(),
                    context,
                    new CreateFamilyMemoryInput(
                            request.getContent(),
                            request.getMemoryType(),
                            request.getVisibility(),
                            summary(request.getTitle(), request.getContent()),
                            defaultImportance(request.getMemoryType()),
                            request.getMetadata())));
            case OBSERVATION -> toolExecutor.execute(new AgentToolCallRequest<>(
                    AgentToolName.CREATE_GROWTH_GUARD_RECORD.value(),
                    context,
                    new CreateGrowthGuardRecordInput(
                            request.getRelatedUserId(),
                            request.getGrowthCategory(),
                            request.getContent(),
                            request.getGrowthSeverity(),
                            LocalDate.now(),
                            LocalDate.now().plusDays(7),
                            request.getVisibility(),
                            request.getMetadata())));
        };
    }

    private static void requireGrowthTarget(SaveWriteCategory category, AgentSaveMemoryToolRequest request) {
        if (category == SaveWriteCategory.OBSERVATION && request.getRelatedUserId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Growth guard target user is required");
        }
    }

    private static String requestId(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return "save-memory-" + UUID.randomUUID();
        }
        return text.length() <= 128 ? text : text.substring(0, 128);
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static List<String> tags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
    }

    private static String summary(String title, String content) {
        String preferred = title == null ? "" : title.trim();
        if (!preferred.isEmpty()) {
            return preferred;
        }
        String text = content == null ? "" : content.trim();
        return text.length() <= 120 ? text : text.substring(0, 120);
    }

    private static Integer defaultImportance(String memoryType) {
        String normalized = memoryType == null ? "" : memoryType.trim().toUpperCase(Locale.ROOT);
        return ("HEALTH_REMINDER".equals(normalized) || "GROWTH_RISK".equals(normalized)) ? 4 : 3;
    }

    private enum SaveWriteCategory {
        RECORD,
        EXPERIENCE,
        OBSERVATION;

        static SaveWriteCategory from(String value) {
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            try {
                return SaveWriteCategory.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "写下分类不支持");
            }
        }
    }
}
