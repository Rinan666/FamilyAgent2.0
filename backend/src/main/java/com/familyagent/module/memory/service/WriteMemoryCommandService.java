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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WriteMemoryCommandService {

    private final MemoryService memoryService;
    private final PersonalMemoryCommandService personalMemoryCommandService;

    @Transactional
    public WriteMemoryResult write(WriteMemoryRequest request) {
        WriteCategory category = WriteCategory.from(request.getWriteCategory());
        if (personalMemoryRequested(request)) {
            return savePersonalMemory(request, category);
        }
        return saveFamilyMemory(request, category);
    }

    private WriteMemoryResult saveFamilyMemory(WriteMemoryRequest request, WriteCategory category) {
        MemoryContentType type = resolveType(request, category);
        CreateFamilyMemoryRequest memoryRequest = new CreateFamilyMemoryRequest();
        memoryRequest.setFamilyId(request.getFamilyId());
        memoryRequest.setContent(request.getContent().trim());
        memoryRequest.setType(type.name());
        memoryRequest.setScope(blankToNull(request.getVisibility()));
        memoryRequest.setSummary(summary(request.getTitle(), request.getContent()));
        memoryRequest.setImportance(defaultImportance(type));
        memoryRequest.setRelatedUserId(request.getRelatedUserId());
        memoryRequest.setTags(normalizeTags(request.getTags()));
        memoryRequest.setMetadata(metadata(request.getMetadata(), category));
        MemoryEntry entry = memoryService.createFamilyMemory(memoryRequest);
        return new WriteMemoryResult(
                "FAMILY_MEMORY",
                entry.getId(),
                category.name(),
                entry.getScope(),
                title(entry.getTitle(), entry.getSummary(), entry.getContent()));
    }

    private WriteMemoryResult savePersonalMemory(WriteMemoryRequest request, WriteCategory category) {
        CreatePersonalMemoryRequest personalRequest = new CreatePersonalMemoryRequest();
        personalRequest.setContent(request.getContent().trim());
        personalRequest.setType(blankToNull(request.getPersonalMemoryType()));
        personalRequest.setVisibility(blankToNull(request.getVisibility()));
        personalRequest.setSelectedFamilyIds(request.getSelectedFamilyIds());
        personalRequest.setSummary(summary(request.getTitle(), request.getContent()));
        personalRequest.setImportance(3);
        personalRequest.setMetadata(request.getMetadata());
        PersonalMemoryView saved = personalMemoryCommandService.create(personalRequest);
        return new WriteMemoryResult(
                "PERSONAL_MEMORY",
                saved.id(),
                category.name(),
                saved.visibility(),
                title(null, saved.summary(), saved.content()));
    }

    private static MemoryContentType resolveType(WriteMemoryRequest request, WriteCategory category) {
        if (category == WriteCategory.OBSERVATION) {
            return MemoryContentType.OBSERVATION;
        }
        if (category == WriteCategory.RECORD) {
            return MemoryContentType.fromDiaryEntryType(request.getDiaryEntryType());
        }
        String requested = blankToNull(request.getMemoryType());
        if (requested != null) {
            MemoryContentType resolved = MemoryContentType.fromFamilyMemoryType(requested);
            if (resolved != null) {
                return resolved;
            }
        }
        return MemoryContentType.EXPERIENCE;
    }

    private static WriteMemoryMetadata metadata(WriteMemoryMetadata requested, WriteCategory category) {
        Map<String, Object> values = requested == null
                ? new HashMap<>()
                : new HashMap<>(requested.toMap());
        values.put("writeCategory", category.name());
        return WriteMemoryMetadata.fromMap(values);
    }

    private static boolean personalMemoryRequested(WriteMemoryRequest request) {
        return MemoryLibraryKind.PERSONAL.name().equalsIgnoreCase(
                blankToNull(request.getMemoryLibrary()));
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

    private enum WriteCategory {
        RECORD,
        EXPERIENCE,
        OBSERVATION;

        private static WriteCategory from(String value) {
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException error) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Memory write category is not supported");
            }
        }
    }
}
