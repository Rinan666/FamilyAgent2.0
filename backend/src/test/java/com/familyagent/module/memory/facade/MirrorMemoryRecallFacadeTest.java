package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MirrorMemoryRecallFacadeTest {

    @Test
    void shouldDelegateAuthorizedMirrorRecall() {
        AuthorizedMemoryRecallService recallService = mock(AuthorizedMemoryRecallService.class);
        AuthorizedMemoryRecallResult result = AuthorizedMemoryRecallResult.builder()
                .query("summary")
                .build();
        when(recallService.recallForMirror(10L, 201L, 101L, "summary", 12, 10))
                .thenReturn(result);
        MirrorMemoryRecallFacade facade = new MirrorMemoryRecallFacade(recallService);

        assertEquals(result, facade.recallForMirror(10L, 201L, 101L, "summary", 12, 10));
        verify(recallService).recallForMirror(10L, 201L, 101L, "summary", 12, 10);
    }
}
