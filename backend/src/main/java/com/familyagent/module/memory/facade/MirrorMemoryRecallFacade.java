package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MirrorMemoryRecallFacade {

    private final AuthorizedMemoryRecallService recallService;

    public AuthorizedMemoryRecallResult recallForMirror(
            Long familyId,
            Long targetUserId,
            Long viewerUserId,
            String query,
            int diaryLimit,
            int memoryLimit) {
        return recallService.recallForMirror(
                familyId,
                targetUserId,
                viewerUserId,
                query,
                diaryLimit,
                memoryLimit);
    }
}
