package com.familyagent.module.agent.service;

import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryLibraryKind;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.dto.AgentSaveMemoryRequest;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentToolExecutor;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.dto.AgentToolCallRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.harness.dto.CreateFamilyMemoryInput;
import com.familyagent.module.agent.harness.dto.CreatePersonalMemoryInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentSaveMemoryCommandService {

    private final AgentToolExecutor toolExecutor;

    public AgentToolCallResult<?> requestSave(AgentSaveMemoryRequest request, Long viewerUserId) {
        MemoryContentType type = requireMemoryType(request.getMemoryType());
        AgentRunContext context = new AgentRunContext(
                requestId(request.getRequestId()),
                request.getFamilyId(),
                viewerUserId,
                request.getSessionId(),
                defaultText(request.getAgentMode(), "family"),
                defaultText(request.getSubject(), "FamilyAgent"),
                defaultText(request.getContextLabel(), "save_memory"));
        if (personalMemoryRequested(request)) {
            return savePersonal(request, context);
        }
        return saveFamily(request, type, context);
    }

    private AgentToolCallResult<?> saveFamily(
            AgentSaveMemoryRequest request,
            MemoryContentType type,
            AgentRunContext context) {
        return toolExecutor.execute(new AgentToolCallRequest<>(
                AgentToolName.CREATE_FAMILY_MEMORY.value(),
                context,
                new CreateFamilyMemoryInput(
                        request.getContent(),
                        type.name(),
                        request.getVisibility(),
                        summary(request.getTitle(), request.getContent()),
                        type == MemoryContentType.OBSERVATION ? 4 : 3,
                        request.getRelatedUserId(),
                        tags(request.getTags()),
                        request.getMetadata())));
    }

    private AgentToolCallResult<?> savePersonal(
            AgentSaveMemoryRequest request,
            AgentRunContext context) {
        return toolExecutor.execute(new AgentToolCallRequest<>(
                AgentToolName.CREATE_PERSONAL_MEMORY.value(),
                context,
                new CreatePersonalMemoryInput(
                        request.getContent(),
                        request.getMemoryType(),
                        request.getVisibility(),
                        summary(request.getTitle(), request.getContent()),
                        3,
                        request.getSelectedFamilyIds(),
                        request.getMetadata())));
    }

    private static MemoryContentType requireMemoryType(String value) {
        MemoryContentType type = MemoryContentType.fromValue(value);
        if (type == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Memory type is not supported");
        }
        return type;
    }

    private static boolean personalMemoryRequested(AgentSaveMemoryRequest request) {
        return MemoryLibraryKind.PERSONAL.name().equalsIgnoreCase(
                defaultText(request.getMemoryLibrary(), MemoryLibraryKind.FAMILY.name()));
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
                .limit(10)
                .toList();
    }

    private static String summary(String title, String content) {
        String preferred = title == null ? "" : title.trim();
        if (!preferred.isEmpty()) {
            return preferred.length() <= 120 ? preferred : preferred.substring(0, 120);
        }
        String text = content == null ? "" : content.trim();
        return text.length() <= 120 ? text : text.substring(0, 120);
    }

}
