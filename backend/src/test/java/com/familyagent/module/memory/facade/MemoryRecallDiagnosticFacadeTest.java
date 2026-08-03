package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryRecallDiagnosticFacadeTest {

    @Mock private AuthorizedMemoryRecallService recallService;
    @InjectMocks private MemoryRecallDiagnosticFacade facade;

    @Test
    void delegatesValidatedViewerRecall() {
        AuthorizedMemoryRecallResult expected = AuthorizedMemoryRecallResult.builder().build();
        when(recallService.recallForFamilyAfterViewerValidated(3L, 8L, "query", 2, 4))
                .thenReturn(expected);

        AuthorizedMemoryRecallResult actual = facade.recallAfterViewerValidated(3L, 8L, "query", 2, 4);

        assertSame(expected, actual);
    }
}
