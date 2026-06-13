package com.familyagent.module.admin.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticRequest;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticResponse;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class MemoryRecallDiagnosticSupport {

    private final PlatformAdminAccessSupport adminAccessSupport;
    private final FamilyService familyService;
    private final AuthorizedMemoryRecallService memoryRecallService;

    MemoryRecallDiagnosticResponse diagnoseMemoryRecall(MemoryRecallDiagnosticRequest request) {
        adminAccessSupport.requirePlatformAdmin();
        if (request == null || request.getFamilyId() == null || request.getViewerUserId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId and viewerUserId are required");
        }
        familyService.getFamilyMember(request.getFamilyId(), request.getViewerUserId());

        int diaryLimit = clampLimit(request.getDiaryLimit(), 3, 10);
        int memoryLimit = clampLimit(request.getMemoryLimit(), 3, 10);
        AuthorizedMemoryRecallResult recall = memoryRecallService.recallForFamilyAfterViewerValidated(
                request.getFamilyId(),
                request.getViewerUserId(),
                request.getQuery(),
                diaryLimit,
                memoryLimit);

        return MemoryRecallDiagnosticResponse.builder()
                .familyId(request.getFamilyId())
                .viewerUserId(request.getViewerUserId())
                .query(recall.getQuery())
                .retrievalMode(recall.getRetrievalMode())
                .embeddingReadyCount(recall.getEmbeddingReadyCount())
                .diaryCount(recall.getDiaryCount())
                .memoryCount(recall.getMemoryCount())
                .growthRecordCount(recall.getGrowthRecordCount())
                .sources(recall.getSources() == null ? List.of() : recall.getSources())
                .build();
    }

    private static int clampLimit(Integer value, int fallback, int max) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return Math.min(value, max);
    }
}
