package com.familyagent.module.memory.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.HeritageSource;
import com.familyagent.common.constant.MemoryLibraryKind;
import com.familyagent.common.constant.PersonalMemoryType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.memory.dto.CreatePersonalMemoryRequest;
import com.familyagent.module.memory.dto.PersonalMemoryView;
import com.familyagent.module.memory.dto.UpdatePersonalMemoryVisibilityRequest;
import com.familyagent.module.memory.dto.WriteMemoryMetadata;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.repository.PersonalMemoryFamilyGrantRepository;
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
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PersonalMemoryCommandService {

    private final MemoryEntryRepository memoryRepository;
    private final PersonalMemoryFamilyGrantRepository grantRepository;
    private final PersonalMemoryVisibilityPolicy visibilityPolicy;
    private final MemoryEmbeddingService embeddingService;
    private final RedissonClient redissonClient;

    @Transactional
    public PersonalMemoryView create(CreatePersonalMemoryRequest request) {
        Long ownerUserId = CurrentUserGuard.currentUserId();
        PersonalMemoryVisibilityPolicy.VisibilityGrant visibility = visibilityPolicy.resolve(
                ownerUserId,
                request.getVisibility(),
                request.getSelectedFamilyIds());
        RLock lock = redissonClient.getLock("memory:create:personal:user:" + ownerUserId);
        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "Operation is too frequent");
            }
            MemoryEntry entry = buildEntry(ownerUserId, request, visibility.visibility());
            memoryRepository.insert(entry);
            replaceGrants(entry.getId(), ownerUserId, visibility.familyIds());
            embeddingService.indexMemoryAfterCommit(entry);
            return PersonalMemoryView.from(entry, visibility.familyIds());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Operation was interrupted");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    public PersonalMemoryView updateVisibility(
            Long memoryId,
            UpdatePersonalMemoryVisibilityRequest request) {
        Long ownerUserId = CurrentUserGuard.currentUserId();
        MemoryEntry entry = requireOwnedPersonalMemory(memoryId, ownerUserId);
        PersonalMemoryVisibilityPolicy.VisibilityGrant visibility = visibilityPolicy.resolve(
                ownerUserId,
                request.getVisibility(),
                request.getSelectedFamilyIds());
        entry.setScope(visibility.visibility());
        memoryRepository.updateById(entry);
        replaceGrants(memoryId, ownerUserId, visibility.familyIds());
        return PersonalMemoryView.from(entry, visibility.familyIds());
    }

    private MemoryEntry buildEntry(
            Long ownerUserId,
            CreatePersonalMemoryRequest request,
            String visibility) {
        String type = normalizeType(request.getType());
        int importance = clamp(request.getImportance() == null ? 3 : request.getImportance(), 1, 5);
        Map<String, Object> metadata = metadata(request.getMetadata());
        MemoryEntry entry = new MemoryEntry();
        entry.setUserId(ownerUserId);
        entry.setFamilyId(null);
        entry.setLibraryKind(MemoryLibraryKind.PERSONAL.name());
        entry.setType(type);
        entry.setScope(visibility);
        entry.setContent(request.getContent().trim());
        entry.setSummary(blankToNull(request.getSummary()));
        entry.setImportance(importance);
        entry.setConfidence(BigDecimal.valueOf(0.85));
        entry.setStatus(EntityStatus.ACTIVE.name());
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichPersonalMemory(
                metadata,
                entry.getContent(),
                entry.getSummary(),
                type,
                importance));
        return entry;
    }

    private MemoryEntry requireOwnedPersonalMemory(Long memoryId, Long ownerUserId) {
        MemoryEntry entry = memoryRepository.findPersonalByIdAndOwner(memoryId, ownerUserId);
        if (entry == null || !EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return entry;
    }

    private void replaceGrants(Long memoryId, Long ownerUserId, List<Long> familyIds) {
        grantRepository.deleteByMemoryId(memoryId);
        familyIds.forEach(familyId -> grantRepository.insertGrant(memoryId, familyId, ownerUserId));
    }

    private String normalizeType(String value) {
        String normalized = value == null || value.isBlank()
                ? PersonalMemoryType.DEFAULT.name()
                : value.trim().toUpperCase(Locale.ROOT);
        if (!PersonalMemoryType.names().contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Personal memory type is not supported");
        }
        return normalized;
    }

    private Map<String, Object> metadata(WriteMemoryMetadata requestMetadata) {
        Map<String, Object> metadata = new HashMap<>();
        if (requestMetadata != null) {
            metadata.putAll(requestMetadata.toMap());
        }
        metadata.putIfAbsent("source", HeritageSource.PERSONAL_ENTRY.name());
        return metadata;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
