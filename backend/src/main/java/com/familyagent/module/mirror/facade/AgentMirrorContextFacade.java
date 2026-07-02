package com.familyagent.module.mirror.facade;

import com.familyagent.module.mirror.dto.MirrorContextResponse;
import com.familyagent.module.mirror.service.MirrorContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentMirrorContextFacade {

    private final MirrorContextService mirrorContextService;

    public String buildMirrorAgentContext(Long familyId, Long targetUserId, String query) {
        MirrorContextResponse response = mirrorContextService.getContext(familyId, targetUserId, query);
        return response == null || response.getMemoryContext() == null ? "" : response.getMemoryContext();
    }
}
