package com.familyagent.module.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.dto.CreateMemoryEntryRequest;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemoryService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;

    private final MemoryEntryRepository memoryRepository;
    private final FamilyService familyService;

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
        return entry;
    }

    public List<MemoryEntry> listMyMemories(int limit) {
        return memoryRepository.findActiveByUserId(CurrentUserGuard.currentUserId(), normalizeLimit(limit));
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
