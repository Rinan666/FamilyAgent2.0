package com.familyagent.module.memory.facade;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UnifiedMemoryIdentityFacade {

    private final MemoryEntryRepository memoryRepository;

    public Long findMemoryEntryId(MemoryOriginType originType, Long originId) {
        if (originType == null || originId == null) {
            return null;
        }
        return memoryRepository.findIdByOrigin(originType.name(), originId);
    }

    public Long requireMemoryEntryId(MemoryOriginType originType, Long originId) {
        Long memoryEntryId = findMemoryEntryId(originType, originId);
        if (memoryEntryId == null || memoryEntryId <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Unified memory record was not found");
        }
        return memoryEntryId;
    }
}
