package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;
import com.familyagent.module.memory.gateway.UnifiedMemorySyncGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnifiedMemorySyncService {

    private final UnifiedMemorySyncGateway syncGateway;

    @Transactional
    public Long sync(UnifiedMemorySyncRequest request) {
        validate(request);
        return syncGateway.upsert(request);
    }

    @Transactional
    public void delete(MemoryOriginType originType, Long originId) {
        if (originType == null || originId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Memory origin is required");
        }
        syncGateway.delete(originType, originId);
    }

    private static void validate(UnifiedMemorySyncRequest request) {
        if (request == null
                || request.ownerUserId() == null
                || request.familyId() == null
                || request.type() == null
                || request.visibility() == null
                || request.originType() == null
                || request.originId() == null
                || request.status() == null
                || request.content() == null
                || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unified memory record is incomplete");
        }
    }
}
