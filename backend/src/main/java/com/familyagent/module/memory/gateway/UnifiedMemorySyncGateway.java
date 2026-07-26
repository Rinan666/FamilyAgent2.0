package com.familyagent.module.memory.gateway;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.facade.UnifiedMemoryCreateResult;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;

public interface UnifiedMemorySyncGateway {

    UnifiedMemoryCreateResult insert(UnifiedMemorySyncRequest request);

    Long upsert(UnifiedMemorySyncRequest request);

    Long delete(MemoryOriginType originType, Long originId);
}
