package com.familyagent.module.memory.facade;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.service.UnifiedMemorySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UnifiedMemorySyncFacade {

    private final UnifiedMemorySyncService syncService;

    public Long sync(UnifiedMemorySyncRequest request) {
        return syncService.sync(request);
    }

    public void delete(MemoryOriginType originType, Long originId) {
        syncService.delete(originType, originId);
    }
}
