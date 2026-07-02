package com.familyagent.module.mirror.facade;

import com.familyagent.module.mirror.dto.MirrorContextResponse;
import com.familyagent.module.mirror.service.MirrorContextService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMirrorContextFacadeTest {

    private final MirrorContextService mirrorContextService = mock(MirrorContextService.class);
    private final AgentMirrorContextFacade facade = new AgentMirrorContextFacade(mirrorContextService);

    @Test
    void buildMirrorAgentContext_usesServerMirrorContext() {
        when(mirrorContextService.getContext(10L, 101L, "choice question"))
                .thenReturn(MirrorContextResponse.builder()
                        .memoryContext("authorized mirror context")
                        .build());

        String context = facade.buildMirrorAgentContext(10L, 101L, "choice question");

        verify(mirrorContextService).getContext(10L, 101L, "choice question");
        assertEquals("authorized mirror context", context);
    }
}
