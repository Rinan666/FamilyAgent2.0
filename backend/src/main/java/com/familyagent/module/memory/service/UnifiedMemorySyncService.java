package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.facade.FamilyMembershipQueryFacade;
import com.familyagent.module.memory.facade.UnifiedMemoryCreateResult;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;
import com.familyagent.module.memory.gateway.UnifiedMemorySyncGateway;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnifiedMemorySyncService {

    private final UnifiedMemorySyncGateway syncGateway;
    private final FamilyMembershipQueryFacade membershipQueryFacade;
    private final MemoryEntryRepository memoryRepository;
    private final MemoryEmbeddingService embeddingService;

    @Transactional
    public UnifiedMemoryCreateResult create(UnifiedMemorySyncRequest request) {
        validate(request, false);
        UnifiedMemoryCreateResult result = syncGateway.insert(normalizeRelatedUser(request));
        index(result.memoryEntryId());
        return result;
    }

    @Transactional
    public Long sync(UnifiedMemorySyncRequest request) {
        validate(request, true);
        Long memoryId = syncGateway.upsert(normalizeRelatedUser(request));
        index(memoryId);
        return memoryId;
    }

    private void index(Long memoryId) {
        MemoryEntry entry = memoryRepository.selectById(memoryId);
        if (entry == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unified memory record was not returned");
        }
        if (EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            embeddingService.indexMemoryAfterCommit(entry);
        } else {
            embeddingService.deleteMemoryIndexAfterCommit(memoryId);
        }
    }

    @Transactional
    public void delete(MemoryOriginType originType, Long originId) {
        if (originType == null || originId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Memory origin is required");
        }
        Long memoryId = syncGateway.delete(originType, originId);
        if (memoryId != null) {
            embeddingService.deleteMemoryIndexAfterCommit(memoryId);
        }
    }

    private static void validate(UnifiedMemorySyncRequest request, boolean requireOriginId) {
        if (request == null
                || request.ownerUserId() == null
                || request.familyId() == null
                || request.type() == null
                || request.visibility() == null
                || request.originType() == null
                || request.status() == null
                || request.occurredAt() == null
                || request.content() == null
                || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unified memory record is incomplete");
        }
        if (requireOriginId != (request.originId() != null)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    requireOriginId ? "Memory origin id is required" : "Memory origin id must be allocated by the server");
        }
    }

    private UnifiedMemorySyncRequest normalizeRelatedUser(UnifiedMemorySyncRequest request) {
        Long relatedUserId = request.relatedUserId();
        if (relatedUserId == null || membershipQueryFacade.isMember(request.familyId(), relatedUserId)) {
            return request;
        }
        return request.withRelatedUserId(null);
    }
}
