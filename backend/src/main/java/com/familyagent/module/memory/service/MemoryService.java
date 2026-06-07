package com.familyagent.module.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.dto.CreateMemoryEntryRequest;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemoryService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;
    private static final Set<String> FAMILY_MEMORY_TYPES = Set.of(
            "FAMILY_STORY", "ELDER_ADVICE", "HEALTH_REMINDER", "GROWTH_RISK", "VALUE");
    private static final Set<String> FAMILY_MEMORY_SCOPES = Set.of(
            "PRIVATE", "PARENT_VISIBLE", "CARE_VISIBLE", "FAMILY_VISIBLE");

    private final MemoryEntryRepository memoryRepository;
    private final FamilyService familyService;
    private final MemoryEmbeddingService memoryEmbeddingService;

    @Transactional
    public MemoryEntry createMemory(CreateMemoryEntryRequest request) {
        Long userId = CurrentUserGuard.currentUserId();
        if (request.getFamilyId() != null) {
            familyService.checkMembership(request.getFamilyId());
        }

        MemoryEntry duplicate = memoryRepository.selectOne(new LambdaQueryWrapper<MemoryEntry>()
                .eq(MemoryEntry::getUserId, userId)
                .eq(MemoryEntry::getStatus, "ACTIVE")
                .eq(MemoryEntry::getContent, request.getContent())
                .last("LIMIT 1"));
        if (duplicate != null) {
            return duplicate;
        }

        MemoryEntry entry = new MemoryEntry();
        entry.setUserId(userId);
        entry.setFamilyId(request.getFamilyId());
        entry.setSubject(blankToNull(request.getSubject()));
        entry.setKnowledgePointId(request.getKnowledgePointId());
        entry.setType(defaultString(request.getType(), "LEARNING"));
        entry.setScope(defaultString(request.getScope(), "PRIVATE"));
        entry.setContent(request.getContent().trim());
        entry.setSummary(blankToNull(request.getSummary()));
        entry.setImportance(clamp(request.getImportance() == null ? 3 : request.getImportance(), 1, 5));
        entry.setConfidence(request.getConfidence() == null ? BigDecimal.valueOf(0.7) : request.getConfidence());
        entry.setSourceSessionId(request.getSourceSessionId());
        entry.setStatus("ACTIVE");
        entry.setMetadata(request.getMetadata() == null ? Map.of() : request.getMetadata());
        memoryRepository.insert(entry);
        memoryEmbeddingService.indexMemoryAfterCommit(entry);
        return entry;
    }

    public List<MemoryEntry> listMyMemories(int limit) {
        return memoryRepository.findActiveByUserId(CurrentUserGuard.currentUserId(), normalizeLimit(limit));
    }

    @Transactional
    public MemoryEntry createFamilyMemory(CreateFamilyMemoryRequest request) {
        Long userId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(request.getFamilyId());

        Map<String, Object> metadata = buildFamilyMemoryMetadata(request);
        Object sourceDiaryId = metadata.get("sourceDiaryId");
        if ("DIARY_PROMOTION".equals(metadata.get("source")) && sourceDiaryId != null) {
            MemoryEntry existing = memoryRepository.findActiveBySourceDiaryId(
                    request.getFamilyId(),
                    String.valueOf(sourceDiaryId));
            if (existing != null) {
                return existing;
            }
        }

        MemoryEntry entry = new MemoryEntry();
        entry.setUserId(userId);
        entry.setFamilyId(request.getFamilyId());
        entry.setType(normalizeFamilyMemoryType(request.getType()));
        entry.setScope(normalizeFamilyMemoryScope(request.getScope()));
        entry.setContent(request.getContent().trim());
        entry.setSummary(blankToNull(request.getSummary()));
        entry.setImportance(clamp(request.getImportance() == null ? 3 : request.getImportance(), 1, 5));
        entry.setConfidence(BigDecimal.valueOf(0.85));
        entry.setStatus("ACTIVE");
        entry.setMetadata(metadata);
        memoryRepository.insert(entry);
        memoryEmbeddingService.indexMemoryAfterCommit(entry);
        return entry;
    }

    public List<MemoryEntry> listFamilyMemories(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        return memoryRepository.findActiveFamilyMemories(
                familyId,
                CurrentUserGuard.currentUserId(),
                normalizeLimit(limit));
    }

    public List<MemoryEntry> recall(String subject, Long knowledgePointId, int limit) {
        return memoryRepository.recall(
                CurrentUserGuard.currentUserId(),
                blankToNull(subject),
                knowledgePointId,
                normalizeLimit(limit));
    }

    @Transactional
    public void archiveMemory(Long id) {
        MemoryEntry entry = memoryRepository.selectById(id);
        if (entry == null || !"ACTIVE".equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        CurrentUserGuard.requireSelf(entry.getUserId());
        entry.setStatus("ARCHIVED");
        memoryRepository.updateById(entry);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalizeFamilyMemoryType(String type) {
        String normalized = type == null ? "ELDER_ADVICE" : type.trim().toUpperCase(Locale.ROOT);
        if (!FAMILY_MEMORY_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "家族经验类型不支持");
        }
        return normalized;
    }

    private static String normalizeFamilyMemoryScope(String scope) {
        String normalized = scope == null ? "FAMILY_VISIBLE" : scope.trim().toUpperCase(Locale.ROOT);
        if (!FAMILY_MEMORY_SCOPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可见范围不支持");
        }
        return normalized;
    }

    private static Map<String, Object> buildFamilyMemoryMetadata(CreateFamilyMemoryRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        if (request.getMemoryCard() != null) {
            metadata.put("memoryCard", request.getMemoryCard());
        }
        metadata.putIfAbsent("source", "HERITAGE_ENTRY");
        return metadata;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
