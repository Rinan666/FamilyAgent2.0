package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryLibraryKind;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.dto.CreatePersonalMemoryRequest;
import com.familyagent.module.memory.dto.PersonalMemoryView;
import com.familyagent.module.memory.dto.WriteMemoryMetadata;
import com.familyagent.module.memory.dto.WriteMemoryRequest;
import com.familyagent.module.memory.dto.WriteMemoryResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WriteMemoryCommandService {

    private final MemoryService memoryService;
    private final PersonalMemoryCommandService personalMemoryCommandService;

    @Transactional
    public WriteMemoryResult write(WriteMemoryRequest request) {
        MemoryContentType type = requireMemoryType(request.getMemoryType());
        if (personalMemoryRequested(request)) {
            return savePersonalMemory(request, type);
        }
        return saveFamilyMemory(request, type);
    }

    private WriteMemoryResult saveFamilyMemory(WriteMemoryRequest request, MemoryContentType type) {
        CreateFamilyMemoryRequest memoryRequest = new CreateFamilyMemoryRequest();
        memoryRequest.setFamilyId(request.getFamilyId());
        memoryRequest.setContent(request.getContent().trim());
        memoryRequest.setType(type.name());
        memoryRequest.setScope(blankToNull(request.getVisibility()));
        memoryRequest.setSummary(summary(request.getTitle(), request.getContent()));
        memoryRequest.setImportance(defaultImportance(type));
        memoryRequest.setRelatedUserId(request.getRelatedUserId());
        memoryRequest.setTags(normalizeTags(request.getTags()));
        memoryRequest.setMetadata(request.getMetadata());
        MemoryEntry entry = memoryService.createFamilyMemory(memoryRequest);
        return new WriteMemoryResult(
                MemoryLibraryKind.FAMILY.name(),
                entry.getId(),
                type.name(),
                entry.getScope(),
                title(entry.getTitle(), entry.getSummary(), entry.getContent()));
    }

    private WriteMemoryResult savePersonalMemory(WriteMemoryRequest request, MemoryContentType type) {
        CreatePersonalMemoryRequest personalRequest = new CreatePersonalMemoryRequest();
        personalRequest.setContent(request.getContent().trim());
        personalRequest.setType(type.name());
        personalRequest.setVisibility(blankToNull(request.getVisibility()));
        personalRequest.setSelectedFamilyIds(request.getSelectedFamilyIds());
        personalRequest.setSummary(summary(request.getTitle(), request.getContent()));
        personalRequest.setImportance(3);
        personalRequest.setMetadata(request.getMetadata());
        PersonalMemoryView saved = personalMemoryCommandService.create(personalRequest);
        return new WriteMemoryResult(
                MemoryLibraryKind.PERSONAL.name(),
                saved.id(),
                type.name(),
                saved.visibility(),
                title(null, saved.summary(), saved.content()));
    }

    private static boolean personalMemoryRequested(WriteMemoryRequest request) {
        return MemoryLibraryKind.PERSONAL.name().equalsIgnoreCase(
                blankToNull(request.getMemoryLibrary()));
    }

    private static MemoryContentType requireMemoryType(String value) {
        MemoryContentType type = MemoryContentType.fromValue(value);
        if (type == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Memory type is not supported");
        }
        return type;
    }

    private static int defaultImportance(MemoryContentType type) {
        return type == MemoryContentType.OBSERVATION ? 4 : 3;
    }

    private static List<String> normalizeTags(List<String> tags) {
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
        String preferred = blankToNull(title);
        if (preferred != null) {
            return preferred.length() <= 120 ? preferred : preferred.substring(0, 120);
        }
        String text = content == null ? "" : content.trim();
        return text.length() <= 120 ? text : text.substring(0, 120);
    }

    private static String title(String title, String summary, String content) {
        for (String value : List.of(
                title == null ? "" : title,
                summary == null ? "" : summary,
                content == null ? "" : content)) {
            if (!value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
