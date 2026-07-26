package com.familyagent.module.memory.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.HeritageSource;
import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.constant.MemoryLibraryKind;
import com.familyagent.common.constant.MemoryType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.dto.CreateMemoryEntryRequest;
import com.familyagent.module.memory.dto.WriteMemoryMetadata;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MemoryService {

    private static final Set<String> FAMILY_MEMORY_SCOPES = MemoryScope.familyNames();

    private final MemoryEntryRepository memoryRepository;
    private final FamilyMembershipFacade familyMembershipFacade;
    private final MemoryEmbeddingService memoryEmbeddingService;
    private final MemoryMergeService memoryMergeService;
    private final MemorySearchService memorySearchService;
    private final MemoryVoteService memoryVoteService;
    private final RedissonClient redissonClient;

    @Transactional
    public MemoryEntry createMemory(CreateMemoryEntryRequest request) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "学习记忆功能已下线，请使用家族记忆、每日记录或成长观察。");
    }

    @Transactional
    public MemoryEntry createFamilyMemory(CreateFamilyMemoryRequest request) {
        Long userId = CurrentUserGuard.currentUserId();
        familyMembershipFacade.checkMembership(request.getFamilyId());

        WriteMemoryMetadata requestMetadata = request.getMetadata();
        Map<String, Object> metadata = buildFamilyMemoryMetadata(requestMetadata);
        Long sourceDiaryId = requestMetadata == null ? null : requestMetadata.getSourceDiaryId();
        if (isDiaryPromotion(requestMetadata) && sourceDiaryId != null) {
            MemoryEntry existing = memoryRepository.findActiveBySourceDiaryId(
                    request.getFamilyId(), sourceDiaryId.toString());
            if (existing != null) {
                return existing;
            }
        }

        String normalizedType = normalizeFamilyMemoryType(request.getType());
        String normalizedScope = normalizeFamilyMemoryScope(request.getScope());

        String lockKey = "memory:create:family:" + request.getFamilyId() + ":user:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "操作过于频繁，请稍后重试");
            }
            MemoryEntry similar = memoryMergeService.findSimilar(request, metadata, userId, normalizedType, normalizedScope);
            if (similar != null) {
                return memoryMergeService.merge(similar, request, metadata, userId);
            }

            MemoryEntry entry = new MemoryEntry();
            entry.setUserId(userId);
            entry.setFamilyId(request.getFamilyId());
            entry.setLibraryKind(MemoryLibraryKind.FAMILY.name());
            entry.setTitle(truncate(blankToNull(request.getSummary()), 120));
            entry.setRelatedUserId(request.getRelatedUserId());
            entry.setType(normalizedType);
            entry.setScope(normalizedScope);
            entry.setContent(request.getContent().trim());
            entry.setSummary(blankToNull(request.getSummary()));
            entry.setImportance(clamp(request.getImportance() == null ? 3 : request.getImportance(), 1, 5));
            entry.setConfidence(BigDecimal.valueOf(0.85));
            entry.setStatus(EntityStatus.ACTIVE.name());
            entry.setOccurredAt(java.time.LocalDateTime.now());
            entry.setTags(normalizeTags(request.getTags()));
            entry.setMetadata(MemoryIndexMetadataBuilder.enrichFamilyMemory(
                    metadata,
                    entry.getContent(),
                    entry.getSummary(),
                    entry.getType(),
                    entry.getImportance()));
            memoryRepository.insert(entry);
            memoryEmbeddingService.indexMemoryAfterCommit(entry);
            return entry;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "操作被中断，请重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public MemoryEntry mergeFamilyMemory(
            MemoryEntry existing,
            CreateFamilyMemoryRequest request,
            Map<String, Object> incomingMetadata,
            Long viewerUserId) {
        return memoryMergeService.merge(existing, request, incomingMetadata, viewerUserId);
    }

    public List<MemoryEntry> listFamilyMemories(Long familyId, int limit) {
        return memorySearchService.listFamilyMemories(familyId, limit);
    }

    public PageResult<MemoryEntry> searchFamilyMemories(
            Long familyId, Long targetUserId, String keyword, int page, int pageSize) {
        return memorySearchService.searchFamilyMemories(familyId, targetUserId, keyword, page, pageSize);
    }

    @Transactional
    public MemoryEntry voteFamilyMemory(Long memoryId, String voteType) {
        return memoryVoteService.vote(memoryId, voteType);
    }

    public List<MemoryEntry> recall(String subject, int limit) {
        return List.of();
    }

    @Transactional
    public void archiveMemory(Long id) {
        MemoryEntry entry = memoryRepository.selectById(id);
        if (entry == null || !EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        CurrentUserGuard.requireSelf(entry.getUserId());
        entry.setStatus(EntityStatus.ARCHIVED.name());
        memoryRepository.updateById(entry);
    }

    private static String normalizeFamilyMemoryType(String type) {
        String requested = type == null ? MemoryType.DEFAULT.name() : type.trim().toUpperCase(Locale.ROOT);
        MemoryContentType normalized = MemoryContentType.fromFamilyMemoryType(requested);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "家族经验类型不支持");
        }
        return normalized.name();
    }

    private static String normalizeFamilyMemoryScope(String scope) {
        String normalized = scope == null ? MemoryScope.DEFAULT_MEMORY.name() : scope.trim().toUpperCase(Locale.ROOT);
        if (!FAMILY_MEMORY_SCOPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可见范围不支持");
        }
        return normalized;
    }

    private static Map<String, Object> buildFamilyMemoryMetadata(WriteMemoryMetadata requestMetadata) {
        Map<String, Object> metadata = new HashMap<>();
        if (requestMetadata != null) {
            metadata.putAll(requestMetadata.toMap());
        }
        metadata.putIfAbsent("source", HeritageSource.HERITAGE_ENTRY.name());
        return metadata;
    }

    private static boolean isDiaryPromotion(WriteMemoryMetadata metadata) {
        return metadata != null
                && HeritageSource.DIARY_PROMOTION.name().equals(blankToNull(metadata.getSource()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String[] normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new String[0];
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .limit(10)
                .toArray(String[]::new);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
