package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryRecallDiagnosticFacade {

    private final AuthorizedMemoryRecallService recallService;

    public AuthorizedMemoryRecallResult recallAfterViewerValidated(
            Long familyId,
            Long viewerUserId,
            String query,
            int diaryLimit,
            int memoryLimit) {
        return recallService.recallForFamilyAfterViewerValidated(
                familyId,
                viewerUserId,
                query,
                diaryLimit,
                memoryLimit);
    }
}
