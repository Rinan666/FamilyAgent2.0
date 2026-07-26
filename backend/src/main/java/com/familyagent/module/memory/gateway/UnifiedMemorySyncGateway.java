package com.familyagent.module.memory.gateway;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;

public interface UnifiedMemorySyncGateway {

    Long upsert(UnifiedMemorySyncRequest request);

    Long delete(MemoryOriginType originType, Long originId);
}
