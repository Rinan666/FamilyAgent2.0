package com.familyagent.module.memory.service;

import com.familyagent.common.constant.FollowUpStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.diary.dto.CreateDiaryEntryRequest;
import com.familyagent.module.diary.dto.DiaryEntryMetadata;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.AgentDiaryEntryFacade;
import com.familyagent.module.growth.dto.CreateGrowthGuardRecordRequest;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.AgentGrowthGuardRecordFacade;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.dto.WriteMemoryMetadata;
import com.familyagent.module.memory.dto.WriteMemoryRequest;
import com.familyagent.module.memory.dto.WriteMemoryResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class WriteMemoryCommandService {

    private static final Set<String> WRITE_CATEGORIES = Set.of("RECORD", "EXPERIENCE", "OBSERVATION");

    private final AgentDiaryEntryFacade diaryEntryFacade;
    private final MemoryService memoryService;
    private final AgentGrowthGuardRecordFacade growthGuardRecordFacade;

    public WriteMemoryCommandService(
            AgentDiaryEntryFacade diaryEntryFacade,
            MemoryService memoryService,
            AgentGrowthGuardRecordFacade growthGuardRecordFacade) {
        this.diaryEntryFacade = diaryEntryFacade;
        this.memoryService = memoryService;
        this.growthGuardRecordFacade = growthGuardRecordFacade;
    }

    @Transactional
    public WriteMemoryResult write(WriteMemoryRequest request) {
        String category = normalizeCategory(request.getWriteCategory());
        return switch (category) {
            case "RECORD" -> saveRecord(request, category);
            case "EXPERIENCE" -> saveExperience(request, category);
            case "OBSERVATION" -> saveObservation(request, category);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "写下分类不支持");
        };
    }

    private WriteMemoryResult saveRecord(WriteMemoryRequest request, String category) {
        CreateDiaryEntryRequest diaryRequest = new CreateDiaryEntryRequest();
        diaryRequest.setFamilyId(request.getFamilyId());
        diaryRequest.setContent(request.getContent().trim());
        diaryRequest.setEntryType(blankToNull(request.getDiaryEntryType()));
        diaryRequest.setTitle(blankToNull(request.getTitle()));
        diaryRequest.setTags(normalizeTags(request.getTags()));
        diaryRequest.setVisibility(blankToNull(request.getVisibility()));
        Map<String, Object> metadata = mutableMetadata(request.getMetadata());
        metadata.put("writeCategory", category);
        metadata.put("disableAutoMerge", true);
        if (request.getRelatedUserId() != null) {
            metadata.put("relatedUserId", request.getRelatedUserId());
        }
        diaryRequest.setMetadata(DiaryEntryMetadata.fromMap(metadata));
        DiaryEntry entry = diaryEntryFacade.create(diaryRequest);
        return new WriteMemoryResult(
                "DIARY_ENTRY",
                entry.getId(),
                category,
                entry.getVisibility(),
                titleOrFallback(request.getTitle(), entry.getStructured(), request.getContent()));
    }

    private WriteMemoryResult saveExperience(WriteMemoryRequest request, String category) {
        CreateFamilyMemoryRequest memoryRequest = new CreateFamilyMemoryRequest();
        memoryRequest.setFamilyId(request.getFamilyId());
        memoryRequest.setContent(request.getContent().trim());
        memoryRequest.setType(blankToNull(request.getMemoryType()));
        memoryRequest.setScope(blankToNull(request.getVisibility()));
        memoryRequest.setSummary(summaryOrFallback(request.getTitle(), request.getContent()));
        memoryRequest.setImportance(defaultImportance(request.getMemoryType()));
        Map<String, Object> metadata = mutableMetadata(request.getMetadata());
        metadata.put("writeCategory", category);
        if (blankToNull(request.getTitle()) != null) {
            metadata.put("title", request.getTitle().trim());
        }
        if (!normalizeTags(request.getTags()).isEmpty()) {
            metadata.put("tags", normalizeTags(request.getTags()));
            metadata.putIfAbsent("scenario", String.join(" ", normalizeTags(request.getTags())));
        }
        memoryRequest.setMetadata(WriteMemoryMetadata.fromMap(metadata));
        MemoryEntry entry = memoryService.createFamilyMemory(memoryRequest);
        return new WriteMemoryResult(
                "FAMILY_MEMORY",
                entry.getId(),
                category,
                entry.getScope(),
                titleOrFallback(request.getTitle(), entry.getSummary(), request.getContent()));
    }

    private WriteMemoryResult saveObservation(WriteMemoryRequest request, String category) {
        if (request.getRelatedUserId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "观察类内容需要先关联一位成员");
        }
        CreateGrowthGuardRecordRequest growthRequest = new CreateGrowthGuardRecordRequest();
        growthRequest.setFamilyId(request.getFamilyId());
        growthRequest.setTargetUserId(request.getRelatedUserId());
        growthRequest.setCategory(blankToNull(request.getGrowthCategory()));
        growthRequest.setContent(request.getContent().trim());
        growthRequest.setSeverity(request.getGrowthSeverity());
        growthRequest.setObservedAt(LocalDate.now());
        growthRequest.setFollowUpAt(LocalDate.now().plusDays(7));
        growthRequest.setVisibility(blankToNull(request.getVisibility()));
        Map<String, Object> metadata = mutableMetadata(request.getMetadata());
        metadata.put("writeCategory", category);
        metadata.putIfAbsent("followUpStatus", FollowUpStatus.PENDING.name());
        if (blankToNull(request.getTitle()) != null) {
            metadata.put("title", request.getTitle().trim());
        }
        if (!normalizeTags(request.getTags()).isEmpty()) {
            metadata.put("tags", normalizeTags(request.getTags()));
        }
        growthRequest.setMetadata(metadata);
        GrowthGuardRecord record = growthGuardRecordFacade.create(growthRequest);
        return new WriteMemoryResult(
                "GROWTH_GUARD",
                record.getId(),
                category,
                record.getVisibility(),
                titleOrFallback(request.getTitle(), null, request.getContent()));
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if (!WRITE_CATEGORIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "写下分类不支持");
        }
        return normalized;
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized.stream().limit(8).toList();
    }

    private static Map<String, Object> mutableMetadata(WriteMemoryMetadata metadata) {
        if (metadata == null) {
            return new HashMap<>();
        }
        return new HashMap<>(metadata.toMap());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Integer defaultImportance(String memoryType) {
        String normalized = memoryType == null ? "" : memoryType.trim().toUpperCase(Locale.ROOT);
        return ("HEALTH_REMINDER".equals(normalized) || "GROWTH_RISK".equals(normalized)) ? 4 : 3;
    }

    @SuppressWarnings("unchecked")
    private static String titleOrFallback(String requestedTitle, Object savedTitle, String content) {
        String requested = blankToNull(requestedTitle);
        if (requested != null) {
            return requested;
        }
        if (savedTitle instanceof Map<?, ?> structured) {
            Object title = structured.get("title");
            if (title instanceof String text && !text.isBlank()) {
                return text.trim();
            }
            Object summary = structured.get("summary");
            if (summary instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        if (savedTitle instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        String trimmed = content == null ? "" : content.trim();
        return trimmed.length() <= 32 ? trimmed : trimmed.substring(0, 32);
    }

    private static String summaryOrFallback(String title, String content) {
        String preferred = blankToNull(title);
        if (preferred != null) {
            return preferred;
        }
        String trimmed = content == null ? "" : content.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }
}
